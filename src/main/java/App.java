import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.ZoneId;
import java.util.TimeZone;

public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    // Credentials
    private static final String WEATHERSTACK_API_KEY = "4ef765d0d9f80ae07038c7507d3f12f5";
    public static final String ACCOUNT_SID = "ACbf0451cda0881a0d159998336e041129";
    public static final String AUTH_TOKEN = "ef2c280eddc45c94a5fc4b19136f817d";
    private static final String TO_PHONE_NUMBER = "+19136872310";  // Your phone number with country code
    private static final String FROM_PHONE_NUMBER = "+18558835408";  // Your Twilio number

    // Location
    private static final String LOCATION = "Ames,Iowa";

    public static void main(String[] args) throws SchedulerException {
        // Test Twilio first
        try {
            Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
            Message message = Message.creator(
                    new com.twilio.type.PhoneNumber(TO_PHONE_NUMBER),
                    new com.twilio.type.PhoneNumber(FROM_PHONE_NUMBER),
                    "Weather Alert Service Starting..."
            ).create();
            System.out.println("Test message sent successfully. SID: " + message.getSid());
        } catch (Exception e) {
            System.err.println("Failed to send test message: " + e.getMessage());
            return;  // Exit if test message fails
        }

        // Initialize the scheduler
        Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

        // Define the job and tie it to our WeatherJob class
        JobDetail job = JobBuilder.newJob(WeatherJob.class)
                .withIdentity("weatherJob", "group1")
                .build();

        // Modified trigger to run every minute
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("weatherTrigger", "group1")
                .startNow() // Start immediately
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMinutes(1) // Run every minute
                        .repeatForever())
                .build();

        // Schedule the job using the job and trigger
        scheduler.scheduleJob(job, trigger);
        scheduler.start();

        System.out.println("Weather alert scheduler started - running every minute. Press Ctrl+C to exit.");
    }

    public static class WeatherJob implements Job {
        private final OkHttpClient client = new OkHttpClient();

        @Override
        public void execute(JobExecutionContext context) {
            try {
                String weatherData = fetchWeatherData();
                sendSMS(weatherData);
                System.out.println("Weather alert sent successfully at: " + java.time.LocalTime.now());
            } catch (Exception e) {
                System.err.println("Error executing weather job: " + e.getMessage());
            }
        }

        private String fetchWeatherData() throws IOException {
            String url = String.format(
                    "http://api.weatherstack.com/current" +
                            "?access_key=%s" +
                            "&query=%s" +
                            "&units=f",  // Use Fahrenheit
                    WEATHERSTACK_API_KEY,
                    LOCATION
            );

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new IOException("Unexpected response " + response);

                JSONObject json = new JSONObject(response.body().string());

                // Check for API errors
                if (json.has("error")) {
                    throw new IOException("API Error: " + json.getJSONObject("error").getString("info"));
                }

                JSONObject current = json.getJSONObject("current");
                int temperature = current.getInt("temperature");
                String description = current.getJSONArray("weather_descriptions")
                        .getString(0);
                int feelsLike = current.getInt("feelslike");
                int humidity = current.getInt("humidity");

                return String.format(
                        "Current weather in Ames, IA at %s:\n" +
                                "Temperature: %d°F (feels like %d°F)\n" +
                                "Conditions: %s\n" +
                                "Humidity: %d%%",
                        java.time.LocalTime.now(),
                        temperature, feelsLike, description, humidity
                );
            }
        }

        private void sendSMS(String messageBody) {
            try {
                Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
                Message message = Message.creator(
                        new com.twilio.type.PhoneNumber(TO_PHONE_NUMBER),
                        new com.twilio.type.PhoneNumber(FROM_PHONE_NUMBER),
                        messageBody
                ).create();
                System.out.println("Message sent with SID: " + message.getSid());
            } catch (Exception e) {
                System.err.println("Failed to send SMS: " + e.getMessage());
            }
        }
    }
}