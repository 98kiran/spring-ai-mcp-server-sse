package org.spring_ai_mcp_server_sse.utils;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.TimeZone;

@Service
public class TimeUtilsService {

    @Tool(description = "Get the server's current date, time, timezone, and locale information")
    public String getServerDateTime() {
        ZonedDateTime now = ZonedDateTime.now();
        String formatted = now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy HH:mm:ss z", Locale.ENGLISH));
        String tz = TimeZone.getDefault().getID();
        Locale locale = Locale.getDefault();

        return String.format("Server date/time: %s | Timezone: %s | Locale: %s",
                formatted, tz, locale.toLanguageTag());
    }
}