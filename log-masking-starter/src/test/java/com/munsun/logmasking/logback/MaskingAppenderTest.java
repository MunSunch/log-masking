package com.munsun.logmasking.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.munsun.logmasking.annotation.Masked;
import com.munsun.logmasking.annotation.MaskType;
import com.munsun.logmasking.autoconfigure.LogMaskingProperties;
import com.munsun.logmasking.core.DefaultMaskingStrategy;
import com.munsun.logmasking.core.FieldMaskingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MaskingAppenderTest {

    private FieldMaskingService maskingService;
    private LoggerContext loggerContext;

    @BeforeEach
    void setUp() {
        maskingService = new FieldMaskingService(new DefaultMaskingStrategy(new LogMaskingProperties()));
        loggerContext = new LoggerContext();
    }

    @Test
    void delegates_unmaskableEvent_unchanged() {
        @SuppressWarnings("unchecked")
        Appender<ILoggingEvent> delegate = mock(Appender.class);
        when(delegate.getName()).thenReturn("console");

        MaskingAppender appender = new MaskingAppender(delegate, maskingService);
        appender.setContext(loggerContext);
        appender.start();

        LoggingEvent event = createEvent("plain message: {}", "hello");
        appender.append(event);

        ArgumentCaptor<ILoggingEvent> captor = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(delegate).doAppend(captor.capture());

        // no @Masked fields on String → event passes through unchanged
        assertThat(captor.getValue()).isSameAs(event);
    }

    @Test
    void masks_event_with_annotated_argument() {
        @SuppressWarnings("unchecked")
        Appender<ILoggingEvent> delegate = mock(Appender.class);
        when(delegate.getName()).thenReturn("console");

        MaskingAppender appender = new MaskingAppender(delegate, maskingService);
        appender.setContext(loggerContext);
        appender.start();

        SensitiveDto dto = new SensitiveDto("secret123");
        LoggingEvent event = createEvent("data: {}", dto);
        appender.append(event);

        ArgumentCaptor<ILoggingEvent> captor = ArgumentCaptor.forClass(ILoggingEvent.class);
        verify(delegate).doAppend(captor.capture());

        ILoggingEvent maskedEvent = captor.getValue();
        assertThat(maskedEvent).isNotSameAs(event);
        assertThat(maskedEvent.getFormattedMessage()).contains("password=***");
        assertThat(maskedEvent.getFormattedMessage()).doesNotContain("secret123");
    }

    @Test
    void skips_already_wrapped_appender() {
        @SuppressWarnings("unchecked")
        Appender<ILoggingEvent> inner = mock(Appender.class);
        when(inner.getName()).thenReturn("console");

        MaskingAppender wrapper = new MaskingAppender(inner, maskingService);
        // name starts with "masking:" prefix
        assertThat(wrapper.getName()).startsWith(MaskingAppender.NAME_PREFIX);
    }

    // -- helpers -----------------------------------------------------------

    private LoggingEvent createEvent(String message, Object arg) {
        Logger logger = loggerContext.getLogger("test");
        LoggingEvent event = new LoggingEvent(
                "fqcn", logger, Level.INFO, message, null, new Object[]{arg});
        return event;
    }

    static class SensitiveDto {
        @Masked(type = MaskType.CREDENTIAL)
        private final String password;

        SensitiveDto(String password) {
            this.password = password;
        }
    }
}
