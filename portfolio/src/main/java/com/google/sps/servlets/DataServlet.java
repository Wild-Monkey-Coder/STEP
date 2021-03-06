// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.sps.servlets;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.ArrayList;
import java.io.IOException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** 
 * Returns comments from the datastore depending on the size request made by
 * the browser. Also handles receiving requests to post new comment data.
 **/
@WebServlet("/data")
public class DataServlet extends HttpServlet {

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ArrayList<String> allComments = doCommentQuery();
        UserService userService = UserServiceFactory.getUserService();

        if (!userService.isUserLoggedIn()) {
            String loginUrl = userService.createLoginURL("/MainPage.html");
            response.setContentType("text");
            response.getWriter().println(loginUrl);
            return;
        } else {
            String logoutUrl = userService.createLogoutURL("/MainPage.html");
            
            // Converts the user information the FE requires to JSON so it can be
            // added to the response and easily processed by the FE.
            String userName = userService.getCurrentUser().getNickname();
            String userId = userService.getCurrentUser().getUserId();
            String userData = convertToJson(new UserData(userName, userId, logoutUrl));
            response.setContentType("application:json;");

            if (allComments.isEmpty()) {
                // If there are no comments, then we only send the user data so
                // the FE doesn't change anything.
                response.getWriter().println(Arrays.asList(userData));
            } else {
               String responseSize = request.getParameter("size");

                // The first item of the response should be the user's data
                allComments.add(0, userData);

                // sends the response depending on the 
                // amount of comments the user specified
                if (responseSize.equals("all") || Integer.parseInt(responseSize) > allComments.size() - 1) {
                    String jsonComments = convertToJson(allComments);
                    response.getWriter().println(jsonComments);
                } else {
                    int responseSizeVal = Integer.parseInt(responseSize);
                    response.getWriter().println(convertToJson(allComments.subList(0, responseSizeVal)));
                }
            }
        }
    }

    /**
     * Converts an object into JSON using Gson, but abstracts the need to make
     * a Gson object in different parts of code.
     **/
    private String convertToJson(Object target) {
        Gson gson = new Gson();
        return gson.toJson(target);
    }

    /**
     * Makes a query to the datastore for user comment data, creates UserComment
     * objects from that data, converts the objects to JSON using Gson for ease
     * of implementation, and returns an array list of type string with the data.
     **/
    private ArrayList<String> doCommentQuery() {
        Query query = new Query("Comment").addSort("timestamp", SortDirection.DESCENDING);

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        PreparedQuery results = datastore.prepare(query);

        // temporary array list to return from this method
        ArrayList<String> tempList = new ArrayList<String>();

        // Iterates over the comments data, uses them to create new UserCommennt objetcs, and adds them
        // to the list
        for (Entity comment : results.asIterable()) {
            long timestamp = (long) comment.getProperty("timestamp");
            long id = comment.getKey().getId();
            String name = (String) comment.getProperty("name");
            String text = (String) comment.getProperty("text");

            UserComment tempComment = new UserComment(name, text, timestamp, id);

            tempList.add(convertToJson(tempComment));
        }

        // returns the templist with the datastore comments
        return tempList;
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        
        // Processes the user comment and adds the timestamp
        String name = request.getParameter("name").trim();
        String text = request.getParameter("user-comment").trim();
        Long timestamp = System.currentTimeMillis();

        // Creates the comment entity and adds the data to it
        Entity newComment = new Entity("Comment");
        newComment.setProperty("name", name);
        newComment.setProperty("text", text);
        newComment.setProperty("timestamp", timestamp);

        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        datastore.put(newComment);

        // Redirect back to main page
        response.sendRedirect("/MainPage.html");

    }

    /**
     * User comment class which allows for the easy grouping of information related
     * to a comment. Because of the implementation of GSON, having all the information
     * related to a comment in a single object allows it to easily be converted to JSON
     * later and will make FE parsing easier.
     **/
    private class UserComment {

        private String userName;
        private String userComment;
        private long commentTimestamp;
        private long commentId;

        public UserComment(String name, String text, long timestamp, long id) {
            userName = name;
            userComment = text;
            commentTimestamp = timestamp;
            commentId = id;
        }

    }

    /**
     * User data class used to organize the name and id of a User object so
     * the Gson.toJson() function can be called and make FE handling easier.
     */
    private class UserData {
        private String name;
        private String id;
        private String logoutUrl;

        public UserData(String name, String id, String logoutUrl) {
            this.name = name;
            this.id = id;
            this.logoutUrl = logoutUrl;
        }
    }

    /**
     * Simple Implementation to reduce need to type print statements in debugging.
     **/
    private void SOP(Object thing) {
        System.out.println(thing);
    }

}