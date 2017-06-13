package com.ywy.editor;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.*;

/**
 * Created by john on 6/12/2017.
 */
@Path("/")
public class TableServices {
    @PUT
    @Path("open")
    @Produces(MediaType.TEXT_PLAIN)
    public int openDB(@Context HttpServletRequest res, @QueryParam("database") String db, @QueryParam("user") String user, @QueryParam("password") String pwd) {
        try {
            // The newInstance() call is a work around for some
            // broken Java implementations
            System.out.println("Open");
            System.out.println("database " + db);

            Class.forName("com.mysql.jdbc.Driver").newInstance();
            Connection conn = DriverManager.getConnection(String.format("jdbc:mysql://%s?user=%s&password=%s", db, user, pwd));
            HttpSession session = res.getSession(true);
            session.setAttribute("conn", conn);
            return 0;
        } catch (Exception ex) {
            // handle the error
            ex.printStackTrace();
            return -1;
        }
    }
    @GET
    @Path("tables")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTables(@Context HttpServletRequest res) {
        Connection conn = (Connection)res.getAttribute("conn");
        try {
            Statement s = conn.createStatement();
            try(ResultSet rs = s.executeQuery("SELECT table_name FROM information_schema.tables")) {
                JSONArray json = new JSONArray();
                while (rs.next()) {
                    json.put(rs.getString(1));
                }
                return Response.ok(json).build();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Response.status(404).build();
    }
    @GET
    @Path("ping")
    @Produces(MediaType.APPLICATION_JSON)
    public Response ping(@Context HttpServletRequest res) {
         JSONObject json = new JSONObject();
         json.put("rest","ok");
                return Response.ok(json).build();
    }
}
