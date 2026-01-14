package com.sudoku;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import org.json.JSONObject;
import org.json.JSONArray;

/**
 * Handles API calls to fetch new Sudoku puzzles
 */
public class ApiHandler {
    private static final String API_URL = "https://sudoku-api.vercel.app/api/dosuku";
    
    /**
     * Fetches a new Sudoku puzzle from the API
     * @return 9x9 integer array representing the puzzle (0 = empty cell)
     * @throws Exception if the API call fails
     */
    public static int[][] fetchNewPuzzle() throws Exception {
        URL url = URI.create(API_URL).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new Exception("API request failed with code: " + responseCode);
        }
        
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(connection.getInputStream())
        );
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        
        // Parse JSON response
        JSONObject jsonResponse = new JSONObject(response.toString());
        JSONObject newboard = jsonResponse.getJSONObject("newboard");
        JSONArray grids = newboard.getJSONArray("grids");
        JSONObject grid = grids.getJSONObject(0);
        JSONArray value = grid.getJSONArray("value");
        
        // Convert JSON array to 2D int array
        int[][] puzzle = new int[9][9];
        for (int i = 0; i < 9; i++) {
            JSONArray row = value.getJSONArray(i);
            for (int j = 0; j < 9; j++) {
                puzzle[i][j] = row.getInt(j);
            }
        }
        
        return puzzle;
    }
}

