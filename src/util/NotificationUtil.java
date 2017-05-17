package util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;

/**
 * Created by trangdp on 17/05/2017.
 */
public class NotificationUtil {

    public static void notify(String message) {
        Notification notification = new Notification("com.trangdp.plugins.netbeans.intellij", "Netbeans To Intellij Plugin", message, NotificationType.INFORMATION);
        Notifications.Bus.notify(notification);

        if(notification.getBalloon() != null) notification.getBalloon().hide();
    }
}
