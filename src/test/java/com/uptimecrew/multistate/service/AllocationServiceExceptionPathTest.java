package com.uptimecrew.multistate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.uptimecrew.multistate.exception.IncomeAllocationFailedException;
import com.uptimecrew.multistate.exception.JurisdictionUnsupportedException;
import com.uptimecrew.multistate.model.WorkDay;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/**
 * Exercises the failure paths of {@link AllocationService}: the service must
 * surface the concrete domain exception unchanged, preserve the underlying
 * cause, and emit a single WARN log line carrying the exception message.
 */
@ExtendWith(MockitoExtension.class)
class AllocationServiceExceptionPathTest {

    private static final String WORKER_ID = "wkr_001";
    private static final BigDecimal TOTAL_INCOME = new BigDecimal("12500.00");
    private static final LocalDate ALLOCATED_FOR = LocalDate.of(2026, 1, 1);
    private static final List<WorkDay> WORK_DAYS = List.of(
            new WorkDay("day_001", WORKER_ID, "ZZ", LocalDate.of(2026, 3, 1)));

    @Mock
    AllocationStrategy strategy;

    private Logger logbackLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logbackLogger = (Logger) LoggerFactory.getLogger(AllocationService.class);
        appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logbackLogger.detachAppender(appender);
    }

    @Test
    void allocate_strategyRejectsJurisdiction_throwsJurisdictionUnsupportedException() {
        when(strategy.allocate(any(), any(), any(), any()))
                .thenThrow(new JurisdictionUnsupportedException("jurisdiction not supported: ZZ"));

        AllocationService subject = new AllocationService(strategy);

        assertThatThrownBy(() -> subject.allocate(WORKER_ID, TOTAL_INCOME, WORK_DAYS, ALLOCATED_FOR))
                .isInstanceOf(JurisdictionUnsupportedException.class)
                .hasMessageContaining("jurisdiction not supported: ZZ");
    }

    @Test
    void allocate_underlyingReadFails_preservesIOExceptionAsRootCause() {
        IncomeAllocationFailedException failure = new IncomeAllocationFailedException(
                "failed reading day-count source for worker " + WORKER_ID,
                new IOException("synthetic cause"));
        when(strategy.allocate(any(), any(), any(), any())).thenThrow(failure);

        AllocationService subject = new AllocationService(strategy);

        assertThatThrownBy(() -> subject.allocate(WORKER_ID, TOTAL_INCOME, WORK_DAYS, ALLOCATED_FOR))
                .isInstanceOf(IncomeAllocationFailedException.class)
                .hasMessageContaining("failed reading day-count source")
                .hasRootCauseInstanceOf(IOException.class)
                .hasRootCauseMessage("synthetic cause");
    }

    @Test
    void allocate_strategyThrows_emitsExactlyOneWarnLogLineWithExceptionMessage() {
        when(strategy.allocate(any(), any(), any(), any()))
                .thenThrow(new JurisdictionUnsupportedException("jurisdiction not supported: ZZ"));

        AllocationService subject = new AllocationService(strategy);

        assertThatThrownBy(() -> subject.allocate(WORKER_ID, TOTAL_INCOME, WORK_DAYS, ALLOCATED_FOR))
                .isInstanceOf(JurisdictionUnsupportedException.class);

        assertThat(appender.list)
                .filteredOn(ev -> ev.getLevel() == Level.WARN)
                .hasSize(1)
                .extracting(ILoggingEvent::getFormattedMessage)
                .allMatch(message -> message.contains("jurisdiction not supported: ZZ"));
    }
}
