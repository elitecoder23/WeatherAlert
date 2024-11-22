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

    // Replace these with your actual credentials
    private static final String WEATHERSTACK_API_KEY = "4ef765d0d9f80ae07038c7507d3f12f5";
    private static final String TWILIO_ACCOUNT_SID = "ACbf0451cda0881a0d159998336e041129";
    private static final String TWILIO_AUTH_TOKEN = "e65877dace1ad2aea258989e1768b3bc";
    private static final String TWILIO_PHONE_NUMBER = "+18558835408";
    private static final String TARGET_PHONE_NUMBER = "9136872310";

    // Location
    private static final String LOCATION = "Ames,Iowa";

    public static void main(String[] args) throws SchedulerException {
        // Initialize the scheduler
        Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

        // Define the job and tie it to our WeatherJob class
        JobDetail job = JobBuilder.newJob(WeatherJob.class)
                .withIdentity("weatherJob", "group1")
                .build();

        // Create a trigger that fires every day at 8:30 AM Central Time
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("weatherTrigger", "group1")
                .withSchedule(CronScheduleBuilder
                        .dailyAtHourAndMinute(8, 30)
                        .inTimeZone(TimeZone.getTimeZone(ZoneId.of("America/Chicago"))))
                .build();

        // Schedule the job using the job and trigger
        scheduler.scheduleJob(job, trigger);
        scheduler.start();

        logger.info("Weather alert scheduler started. Press Ctrl+C to exit.");
    }

    public static class WeatherJob implements Job {
        private final OkHttpClient client = new OkHttpClient();

        @Override
        public void execute(JobExecutionContext context) {
            try {
                String weatherData = fetchWeatherData();
                sendSMS(weatherData);
                logger.info("Weather alert sent successfully");
            } catch (Exception e) {
                logger.error("Error executing weather job", e);
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
                        "Good morning! Current weather in Ames, IA:\n" +
                                "Temperature: %d°F (feels like %d°F)\n" +
                                "Conditions: %s\n" +
                                "Humidity: %d%%",
                        temperature, feelsLike, description, humidity
                );
            }
        }

        private void sendSMS(String message) {
            Twilio.init(TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN);
            Message.creator(
                    new PhoneNumber(TARGET_PHONE_NUMBER),
                    new PhoneNumber(TWILIO_PHONE_NUMBER),
                    message
            ).create();
        }
    }
}