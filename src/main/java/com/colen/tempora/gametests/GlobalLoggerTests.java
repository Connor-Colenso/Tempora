package com.colen.tempora.gametests;

import static com.colen.tempora.gametests.TestSupport.assertWellFormed;
import static com.colen.tempora.gametests.TestSupport.eventsSince;

import java.util.ArrayList;
import java.util.List;

import com.colen.tempora.Tempora;
import com.colen.tempora.TemporaEvents;
import com.colen.tempora.loggers.generic.GenericEventInfo;
import com.colen.tempora.loggers.weather.WeatherEventInfo;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.Weather;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

/** The global loggers, whose events belong to the whole server rather than a place. */
@GameTestHolder(Tempora.MODID)
public class GlobalLoggerTests {

    // Rain takes a while to set in: the world counts as raining once its rain strength passes 0.2.
    @GameTest(timeoutTicks = 300)
    public static void weatherChangeIsLogged(GameTestHelper helper) {
        int dim = helper.getWorld().provider.dimensionId;
        long since = System.currentTimeMillis();
        Probe<List<GenericEventInfo>> probe = new Probe<>(helper, "weatherChangeIsLogged");

        helper.startSequence()
            // A tick of clear weather first, so the logger has seen it clear before it starts raining.
            .thenExecute("clear", () -> helper.setWeather(Weather.CLEAR))
            .thenIdle(5)
            .thenExecute("start raining", () -> helper.setWeather(Weather.RAIN))
            .thenWaitUntil(
                "the change to rain is logged",
                250,
                () -> helper.assertFalse(
                    rainEvents(probe.poll(() -> eventsSince(TemporaEvents.weatherLogger, since)), dim).isEmpty(),
                    "no change to rain logged"))
            .thenExecute("event reads back intact", () -> {
                WeatherEventInfo event = rainEvents(probe.latest(), dim).get(0);
                helper.assertEquals("CLEAR", event.previousWeather, "previousWeather");
                assertWellFormed(helper, TemporaEvents.weatherLogger, probe.latest());
            })
            .thenSucceed();
    }

    private static List<WeatherEventInfo> rainEvents(List<GenericEventInfo> events, int dim) {
        List<WeatherEventInfo> rain = new ArrayList<>();
        for (GenericEventInfo event : events) {
            WeatherEventInfo weather = (WeatherEventInfo) event;
            if (weather.dimensionID == dim && "RAIN".equals(weather.weather)) rain.add(weather);
        }
        return rain;
    }
}
