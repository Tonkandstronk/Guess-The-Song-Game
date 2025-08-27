package guessTheSong;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;

public class GuessTheSong extends Application {

    private int correctIndex = 0;
    private Button leftAnswer = new Button();
    private Button rightAnswer = new Button();
    private Button[] answers = {leftAnswer, rightAnswer};

    private Label songImage = new Label();
    private ProgressBar timerBar = new ProgressBar(1.0);

    private int totalTime = 10;
    private int[] timeLeft = {totalTime};

    private MediaPlayer mediaPlayer;

    @Override
    public void start(Stage primaryStage) {

        // Main grid
        GridPane mainGrid = new GridPane();
        mainGrid.setStyle("-fx-background-color: LightGrey;");

        RowConstraints topRow = new RowConstraints();
        topRow.setPercentHeight(50);
        RowConstraints bottomRow = new RowConstraints();
        bottomRow.setPercentHeight(50);
        mainGrid.getRowConstraints().addAll(topRow, bottomRow);

        // Timer ProgressBar
        HBox progressContainer = new HBox(timerBar);
        progressContainer.setAlignment(Pos.BOTTOM_CENTER);
        progressContainer.setMaxWidth(Double.MAX_VALUE);
        mainGrid.add(progressContainer, 0, 0);
        timerBar.setPrefHeight(25);

        timerBar.setStyle(
                "-fx-accent: #4caf50;" +
                        "-fx-control-inner-background: #ddd;" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-radius: 10;"
        );

        // Song image container
        songImage.setAlignment(Pos.CENTER);
        VBox imageBox = new VBox(songImage, timerBar);
        imageBox.setAlignment(Pos.CENTER);
        mainGrid.add(imageBox, 0, 0);

        // Bottom buttons
        GridPane bottomGrid = new GridPane();
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        col1.setHgrow(Priority.ALWAYS);
        bottomGrid.getColumnConstraints().addAll(col1);

        for (int i = 0; i < answers.length; i++) {
            final int index = i;
            answers[i].setOnAction(e -> handleAnswer(index));
            answers[i].setPrefSize(300, 100);
            bottomGrid.add(answers[i], i, 0);
        }

        mainGrid.add(bottomGrid, 0, 1);

        // Start first round
        newRound();

        Scene scene = new Scene(mainGrid, 600, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Guess The Song");
        primaryStage.show();
    }

    private void newRound() {
        try {
            // Reset timer
            timeLeft[0] = totalTime;
            timerBar.setProgress(1.0);

            // Stop any previous track
            if (mediaPlayer != null) {
                mediaPlayer.stop();
            }

            // Pick a random track
            SpotifyHelper.Track track = SpotifyHelper.getRandomTrack("drake");

            // Show album art
            Image img = new Image(track.imageUrl);
            songImage.setGraphic(new ImageView(img));
            songImage.setText("");

            // Play preview
            if (!track.previewUrl.isEmpty()) {
                Media media = new Media(track.previewUrl);
                mediaPlayer = new MediaPlayer(media);
                mediaPlayer.play();
            }

            // Pick correct answer randomly
            correctIndex = new Random().nextInt(answers.length);
            answers[correctIndex].setText(track.name);

            // Fill the other button with a dummy/incorrect name
            answers[(correctIndex + 1) % 2].setText("Fake Song " + new Random().nextInt(100));

            // Start countdown
            Timeline timeline = new Timeline(
                    new KeyFrame(Duration.seconds(1), e -> {
                        timeLeft[0]--;
                        timerBar.setProgress(timeLeft[0] / (double) totalTime);

                        if (timeLeft[0] <= 0) {
                            ((Timeline) e.getSource()).stop();
                            songImage.setText("Time's up! The correct song was: " + answers[correctIndex].getText());
                            // Automatically start next round after 2 seconds
                            new Timeline(new KeyFrame(Duration.seconds(2), ev -> newRound())).play();
                        }
                    })
            );
            timeline.setCycleCount(totalTime);
            timeline.play();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleAnswer(int userIndex) {
        if (userIndex == correctIndex) {
            songImage.setText("CORRECT! You guessed it in " + (totalTime - timeLeft[0]) + " seconds!");
        } else {
            songImage.setText("WRONG! The correct song was: " + answers[correctIndex].getText());
        }

        // Start next round after 2 seconds
        new Timeline(new KeyFrame(Duration.seconds(2), e -> newRound())).play();
    }

    public static void main(String[] args) {
        launch(args);
    }

    // SpotifyHelper (same as before)
    public static class SpotifyHelper {
        private static final String CLIENT_ID = "92cd7a0252344686b3590ad6e783c44c";
        private static final String CLIENT_SECRET = "8345d782b4e0492da42b68c1661222aa";
        private static String accessToken = null;

        private static void authenticate() throws IOException, InterruptedException {
            String auth = CLIENT_ID + ":" + CLIENT_SECRET;
            String encoded = Base64.getEncoder().encodeToString(auth.getBytes());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://accounts.spotify.com/api/token"))
                    .header("Authorization", "Basic " + encoded)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            JSONObject json = new JSONObject(response.body());
            accessToken = json.getString("access_token");
        }

        public static Track getRandomTrack(String query) throws IOException, InterruptedException {
            if (accessToken == null) authenticate();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.spotify.com/v1/search?q=" + query + "&type=track&limit=10"))
                    .header("Authorization", "Bearer " + accessToken)
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            JSONObject json = new JSONObject(response.body());
            JSONArray tracks = json.getJSONObject("tracks").getJSONArray("items");

            if (tracks.length() == 0) {
                throw new RuntimeException("No tracks found for query: " + query);
            }

            JSONObject track = tracks.getJSONObject(new Random().nextInt(tracks.length()));

            String name = track.getString("name");
            String previewUrl = track.optString("preview_url", "");
            String imageUrl = track.getJSONObject("album").getJSONArray("images").getJSONObject(0).getString("url");

            return new Track(name, previewUrl, imageUrl);
        }

        public static class Track {
            public String name;
            public String previewUrl;
            public String imageUrl;

            public Track(String name, String previewUrl, String imageUrl) {
                this.name = name;
                this.previewUrl = previewUrl;
                this.imageUrl = imageUrl;
            }
        }
    }
}
