package com.munsun.logmasking.logback;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.munsun.logmasking.core.FieldMaskingService;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Wraps all existing Logback appenders with {@link MaskingAppender} at
 * application startup. Registered as a Spring bean by auto-configuration.
 */
public class MaskingAppenderRegistrar implements SmartInitializingSingleton {

    private final FieldMaskingService maskingService;

    public MaskingAppenderRegistrar(FieldMaskingService maskingService) {
        this.maskingService = maskingService;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext ctx)) {
            return;
        }
        for (Logger logger : ctx.getLoggerList()) {
            wrapAppenders(logger);
        }
    }

    private void wrapAppenders(Logger logger) {
        List<Appender<ILoggingEvent>> originals = new ArrayList<>();
        Iterator<Appender<ILoggingEvent>> it = logger.iteratorForAppenders();
        while (it.hasNext()) {
            Appender<ILoggingEvent> appender = it.next();
            if (appender.getName() != null && appender.getName().startsWith(MaskingAppender.NAME_PREFIX)) {
                continue; // already wrapped
            }
            originals.add(appender);
        }

        for (Appender<ILoggingEvent> original : originals) {
            logger.detachAppender(original);
            MaskingAppender wrapper = new MaskingAppender(original, maskingService);
            wrapper.setContext(logger.getLoggerContext());
            wrapper.start();
            logger.addAppender(wrapper);
        }
    }
}
