package com.ywy.editor;

import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Session;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by john on 6/12/2017.
 */
@Path("/")
public class TableServices {
    static String connString = null;
    @POST
    @Path("open")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response openDB(String objstr ) {
        JSONObject json = new JSONObject();
        try {
            // The newInstance() call is a work around for some
            // broken Java implementations
            System.out.println("Open");
            JSONObject obj = new JSONObject(objstr);
            String db =obj.getString("db");
            String user =obj.getString("user");
            String pwd =obj.getString("pwd");

            System.out.println("database " + db);

            Class.forName("com.mysql.jdbc.Driver").newInstance();
            connString = String.format("jdbc:mysql://%s?useSSL=false&user=%s&password=%s", db, user, pwd);
            System.out.println(connString);
            try (Connection conn = DriverManager.getConnection(connString)){
            }
            json.put("rest","ok");
            return Response.ok(json.toString()).build();
        } catch (Exception ex) {
            // handle the error
            ex.printStackTrace();
            json.put("error",ex.getMessage());
            connString = null;
            return Response.status(404).entity(json.toString()).build();
        }
    }
    @GET
    @Path("tables")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTables(@Context Session session, @Context Request res) {
        if (connString != null) {
            try(Connection conn = DriverManager.getConnection(connString)) {
                Statement s = conn.createStatement();
                try (ResultSet rs = s.executeQuery("SELECT TABLE_SCHEMA,TABLE_NAME FROM information_schema.tables where TABLE_TYPE='BASE TABLE'")) {
                    JSONArray json = new JSONArray();

                    while (rs.next()) {
                        JSONObject obj = new JSONObject();
                        for(int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {

                            obj.put(rs.getMetaData().getColumnName(i), rs.getObject(i));

                        }
                        json.put(rs.getString(1) + "." + rs.getString(2));
                    }
                    return Response.ok(json.toString()).build();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return Response.status(404).build();
    }
    @GET
    @Path("tables/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTableData(@Context Session session, @PathParam("name") String tableName) {
        if (connString != null) {
            try(Connection conn = DriverManager.getConnection(connString)) {
                Set<String> pcols = getPrimaryKeyColumnsForTable(conn, tableName);
                Statement s = conn.createStatement();
                try (ResultSet rs = s.executeQuery("SELECT * FROM " + tableName)) {
                    JSONObject retobj = new JSONObject();
                    retobj.put("table", tableName);
                    JSONArray json = new JSONArray();
                    ResultSetMetaData medaData = rs.getMetaData();
                    JSONObject metaObj = new JSONObject();
                    JSONArray cols = new JSONArray();
                    int size = medaData.getColumnCount();
                    metaObj.put("size",size);
                    for (int i = 1; i <= size; i++) {
                        JSONObject obj = new JSONObject();
                        obj.put("name",medaData.getColumnName(i));
                        obj.put("key", pcols.contains(medaData.getColumnName(i)));
                        obj.put("type", medaData.getColumnTypeName(i));
                        obj.put("incre", medaData.isAutoIncrement(i));
                        cols.put(obj);
                    }
                    metaObj.put("columns", cols);
                    while (rs.next()) {
                        JSONObject obj = new JSONObject();
                        for(int i = 1; i <= size; i++) {
                            obj.put(medaData.getColumnName(i), rs.getObject(i));

                        }
                        json.put(obj);

                    }
                    retobj.put("data", json);
                    retobj.put("metadata",cols);
                    return Response.ok(retobj.toString()).build();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return Response.status(404).build();
    }
    @POST
    @Path("tables/{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveTableData(@Context Session session, @PathParam("name") String tableName, String objstr ) {
        if (connString != null) {
            ArrayList<String> dataFields = new ArrayList<String>();
            ArrayList<String> autoFields = new ArrayList<String>();
            ArrayList<String> keyFields = new ArrayList<String>();
            JSONObject obj = new JSONObject(objstr);
            JSONArray metadata = obj.getJSONArray("metadata");
            for(int i = 0 ; i < metadata.length(); i++) {
                JSONObject col = metadata.getJSONObject(i);
                if (col.getBoolean("incre")) {
                    autoFields.add(col.getString("name"));
                }
                else if (col.getBoolean("key")) {
                    keyFields.add(col.getString("name"));
                }
                else {
                    dataFields.add(col.getString("name"));
                }
            }
            StringBuffer updatesql;
            StringBuffer insertSql = new StringBuffer("INSERT INTO ").append(tableName).append("(").append(String.join("," , dataFields));
            if (keyFields.size() > 0) insertSql.append(",").append(String.join("," , keyFields));
            if (autoFields.size() > 0) insertSql.append(",").append(String.join("," , autoFields));
            insertSql.append(") VALUES(").append(String.join(",", Collections.nCopies(dataFields.size() + keyFields.size() + autoFields.size() ,"?"))).append(")");
            insertSql.append(" ON DUPLICATE KEY UPDATE ").append(String.join(",", dataFields.stream().map(x->x+"= ?").collect(Collectors.toList())));

            System.out.println(insertSql);

            try(Connection conn = DriverManager.getConnection(connString)) {
                conn.setAutoCommit(false);
                Set<String> pFields = getPrimaryKeyColumnsForTable(conn, tableName);
                JSONArray data = obj.getJSONArray("data");
                if (data != null && pFields.size() > 0) {
                    for(int i = 0 ; i < data.length(); i++) {
                        PreparedStatement st = conn.prepareStatement(insertSql.toString());
                        JSONObject rec = data.getJSONObject(i);
                        AtomicInteger fieldInx = new AtomicInteger(1);
                        Function<String, Integer> setFields = (x) -> {
                            int colIndex = fieldInx.getAndIncrement();
                            try {
                                if (rec.has(x) && rec.get(x) != null) {
                                    st.setObject(colIndex, rec.get(x));
                                }else {
                                    st.setObject(colIndex,null);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            return colIndex;
                        };
                        dataFields.forEach(x -> {
                            setFields.apply(x);
                        });
                        keyFields.forEach(x -> {
                            setFields.apply(x);
                        });
                        autoFields.forEach(x -> {
                            setFields.apply(x);
                        });
                        dataFields.forEach(x -> {
                            setFields.apply(x);
                        });

                        System.out.println(st);
                        st.executeUpdate();
                    }
                    conn.commit();

                }
                return getTableData(session,tableName);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        return Response.status(404).build();
    }
    @GET
    @Path("ping")
    @Produces(MediaType.APPLICATION_JSON)
    public Response ping(@Context Request res) {
         JSONObject json = new JSONObject();
         json.put("rest","ok");
                return Response.ok().entity(json.toString()).build();
    }
    public static Set<String> getPrimaryKeyColumnsForTable(Connection connection, String tableName) throws SQLException {
        String[] tnames = tableName.split("\\.");
        try (ResultSet pkColumns = connection.getMetaData().getPrimaryKeys(tnames.length > 1? tnames[0]:null, null, tnames.length> 1?tnames[1]:tableName)) {
            TreeSet<String> pkColumnSet = new TreeSet<String>();
            while (pkColumns.next()) {
                String pkColumnName = pkColumns.getString("COLUMN_NAME");
                Integer pkPosition = pkColumns.getInt("KEY_SEQ");
                System.out.println("" + pkColumnName + " is the " + pkPosition + ". column of the primary key of the table " + tableName);
                pkColumnSet.add(pkColumnName);
            }
            return pkColumnSet;
        }
    }
}
