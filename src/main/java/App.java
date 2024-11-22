import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

public class App {
    // Find your Account Sid and Token at twilio.com/console
    public static final String ACCOUNT_SID = "ACbf0451cda0881a0d159998336e041129";
    public static final String AUTH_TOKEN = "ef2c280eddc45c94a5fc4b19136f817d";

    public static void main(String[] args) {
        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);

        Message message = Message.creator(
                new com.twilio.type.PhoneNumber("+18777804236"),
                new com.twilio.type.PhoneNumber("+18558835408"),
                "How is it going?"
        ).create();  // Moved the .create() outside the parentheses and added missing )

        System.out.println(message.getSid());
    }
}