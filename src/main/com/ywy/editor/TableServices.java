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
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by john on 6/12/2017.
 */
@Path("/")
public class TableServices {
    static String connString = null;
    @PUT
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
                try (ResultSet rs = s.executeQuery("SELECT TABLE_NAME,  TABLE_SCHEMA FROM information_schema.tables where TABLE_TYPE='BASE TABLE'")) {
                    JSONArray json = new JSONArray();

                    while (rs.next()) {
                        JSONObject obj = new JSONObject();
                        for(int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {

                            obj.put(rs.getMetaData().getColumnName(i), rs.getObject(i));

                        }
                        json.put(obj);
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
                        obj.put("isKey", pcols.contains(medaData.getColumnName(i)));
                        obj.put("type", medaData.getColumnTypeName(i));
                        cols.put( new JSONObject().put(medaData.getColumnName(i),obj));
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
    @PUT
    @Path("tables/{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveTableData(@Context Session session, @PathParam("name") String tableName, String objstr ) {
        if (connString != null) {
            ArrayList<String> dataFields = new ArrayList<String>();
            ArrayList<String> autoFields = new ArrayList<String>();

            try(Connection conn = DriverManager.getConnection(connString)) {
                Set<String> pFields = getPrimaryKeyColumnsForTable(conn, tableName);
                Statement s = conn.createStatement();
                try (ResultSet rs = s.executeQuery("SELECT * FROM " + tableName + " where  1=0")) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    for (int i = 1; i <= metaData.getColumnCount(); i++) {
                        String colName = metaData.getCatalogName(i);
                        if (metaData.isAutoIncrement(i)) {
                            autoFields.add(colName);
                        } else if (!pFields.contains(colName)) {
                            dataFields.add(colName);
                        }
                    }
                }
                JSONObject obj = new JSONObject(objstr);
                JSONArray data = obj.getJSONArray("data");
                if (data != null) {
                    for(int i = 0 ; i < data.length(); i++) {
                        JSONObject rec = data.getJSONObject(i);
                        boolean insert = true;
                        for (String a : autoFields) {
                            if (rec.get(a) != null && rec.getInt(a) != 0) {
                                //update statement
                                insert = false;
                                break;
                            }

                        }
                        if (insert) {
                            String sql = "INSERT INTO " + tableName + "(" + String.join("," , dataFields) + ")";
                        }
                        else {

                        }
                        conn.commit();
                    }

                }
                PreparedStatement st = conn.prepareStatement(sql);
            }
            catch (SQLException e) {
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
