package com.doertutorial;

import com.doer.DoerService;
import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

public class AppConfig {
    @Inject
    DoerService doerService;

    public void onApplicationStarted(@Observes StartupEvent startup) {
        Log.info("----- Starting Doer ----");
        doerService.start(true);
    }

    public void onApplicationShutdown(@Observes ShutdownEvent shutdown) {
        doerService.stop();
    }
}
