package oracle.packages.ignite;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.CacheEntryProcessor;
import org.apache.ignite.transactions.Transaction;
import org.apache.ignite.transactions.TransactionConcurrency;
import org.apache.ignite.transactions.TransactionIsolation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.cache.processor.MutableEntry;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Транзакционный сценарий: запись → SELECT → invoke delete → rollback.
 * <p>
 * Тесты с {@code @Tag("ignite-known-failure")} по умолчанию исключены из Surefire
 * ({@code ignite.surefire.excludedGroups}); полный прогон:
 * {@code mvn test -Dignite.surefire.excludedGroups=}.
 */
class IgniteTmpPersonConcurrentTransactionIT {

    /** KV/invoke: SELECT в той же tx на stock Calcite часто не видит запись. */
    static final String TAG_KNOWN_FAILURE = "ignite-known-failure";

    static final String CACHE_NAME = "TBG_TMP_PERSON";
    static final String KEY_TYPE = "TBG_TMP_PERSON_KEY";
    static final String VALUE_TYPE = "TBG_TMP_PERSON_ROW";

    private static final BigDecimal TEST_PID = new BigDecimal("9001");
    private static final String TEST_NAME = "tx-concurrent-name";
    private static final BigDecimal TEST_DEP_ID = new BigDecimal("7");
    private static final BigDecimal TEST_AGE = new BigDecimal("30");

    /** Параллельный INSERT одного PK через SQL в Ignite завершается conflict — потоки по очереди. */
    private static final Object DML_SAME_KEY_LOCK = new Object();

    private static Ignite ignite;

    @BeforeAll
    static void startIgniteAndCreateTable() {
        ignite = IgniteTestServer.start();
        IgniteSqlScriptRunner.applyClasspathScript(ignite, "ignite/tmp_person_transaction.sql");
    }

    @AfterAll
    static void stopIgnite() {
        IgniteTestServer.stop();
    }

    @Test
    @Tag(TAG_KNOWN_FAILURE)
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void twoThreads_describedScenario_invokeKeyType_selectThenDeleteThenRollback() throws Exception {
        TwoThreadOutcome outcome = runTwoThreadsConcurrently(IgniteTmpPersonConcurrentTransactionIT::runDescribedWorkerScenario);

        assertThat(outcome.first().rowFound())
                .as("thread1 %s, thread2 %s", outcome.first(), outcome.second())
                .isTrue();
        assertThat(outcome.first().deleteSucceeded()).isTrue();
        assertThat(outcome.second().rowFound()).isTrue();
        assertThat(outcome.second().deleteSucceeded()).isTrue();

        assertTableEmptyAfterCommittedReads();
    }

    @Test
    @Tag(TAG_KNOWN_FAILURE)
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void oneThread_sqlRowLayout_cachePut_invokeDelete_selectThenRollback() {
        WorkerResult result = runSqlRowLayoutCachePutWorkerScenario();

        assertThat(result.rowFound())
                .as("WorkerResult=%s (SELECT в той же tx после KV-записи)", result)
                .isTrue();
        assertThat(result.deleteSucceeded()).isTrue();
        assertTableEmptyAfterCommittedReads();
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void twoThreads_dmlInsert_selectThenInvokeDeleteThenRollback() throws Exception {
        TwoThreadOutcome outcome = runTwoThreadsConcurrently(IgniteTmpPersonConcurrentTransactionIT::runDmlInsertWorkerScenario);

        assertThat(outcome.first().rowFound())
                .as("thread1 %s, thread2 %s", outcome.first(), outcome.second())
                .isTrue();
        assertThat(outcome.first().deleteSucceeded()).isTrue();
        assertThat(outcome.second().rowFound()).isTrue();
        assertThat(outcome.second().deleteSucceeded()).isTrue();

        assertTableEmptyAfterCommittedReads();
    }

    private record WorkerResult(boolean rowFound, boolean deleteSucceeded) {
    }

    private record TwoThreadOutcome(WorkerResult first, WorkerResult second) {
    }

    private static TwoThreadOutcome runTwoThreadsConcurrently(Supplier<WorkerResult> worker) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        try {
            Future<WorkerResult> first = pool.submit(() -> {
                awaitStart(startGate);
                return worker.get();
            });
            Future<WorkerResult> second = pool.submit(() -> {
                awaitStart(startGate);
                return worker.get();
            });
            startGate.countDown();

            return new TwoThreadOutcome(
                    first.get(90, TimeUnit.SECONDS),
                    second.get(90, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private static void awaitStart(CountDownLatch startGate) {
        try {
            startGate.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for concurrent start", e);
        }
    }

    private static WorkerResult runDescribedWorkerScenario() {
        Transaction tx = null;
        try {
            tx = ignite.transactions().txStart(
                    TransactionConcurrency.PESSIMISTIC,
                    TransactionIsolation.READ_COMMITTED);

            IgniteCache<BinaryObject, BinaryObject> cache = ignite.cache(CACHE_NAME).withKeepBinary();

            BinaryObject key = ignite.binary().builder(KEY_TYPE)
                    .setField("PID", TEST_PID)
                    .build();
            BinaryObject value = ignite.binary().builder(VALUE_TYPE)
                    .setField("NAME", TEST_NAME)
                    .setField("DEP_ID", TEST_DEP_ID)
                    .setField("AGE", TEST_AGE)
                    .build();

            cache.invoke(key, new PutBinaryKeyValueProcessor(), value);

            boolean rowFound = matchesExpectedRow(queryPersonByPidAndName());
            Boolean removed = cache.invoke(key, new RemoveBinaryKeyProcessor());
            boolean deleteSucceeded = Boolean.TRUE.equals(removed);

            tx.rollback();
            return new WorkerResult(rowFound, deleteSucceeded);
        } catch (Exception e) {
            if (tx != null) {
                tx.rollback();
            }
            throw new RuntimeException(e);
        }
    }

    private static WorkerResult runDmlInsertWorkerScenario() {
        synchronized (DML_SAME_KEY_LOCK) {
            deletePersonViaDmlIfPresent();

            Transaction tx = null;
            try {
                tx = ignite.transactions().txStart(
                        TransactionConcurrency.PESSIMISTIC,
                        TransactionIsolation.READ_COMMITTED);

                IgniteCache<BinaryObject, BinaryObject> cache = ignite.cache(CACHE_NAME).withKeepBinary();

                insertPersonViaDml();

                boolean rowFound = matchesExpectedRow(queryPersonByPidAndName());

                BinaryObject key = ignite.binary().builder(KEY_TYPE)
                        .setField("PID", TEST_PID)
                        .build();
                Boolean removed = cache.invoke(key, new RemoveBinaryKeyProcessor());
                boolean deleteSucceeded = Boolean.TRUE.equals(removed);

                tx.rollback();
                return new WorkerResult(rowFound, deleteSucceeded);
            } catch (Exception e) {
                if (tx != null) {
                    tx.rollback();
                }
                throw new RuntimeException(e);
            } finally {
                deletePersonViaDmlIfPresent();
            }
        }
    }

    private static void deletePersonViaDmlIfPresent() {
        IgniteSqlScriptRunner.executeUpdate(
                ignite,
                CACHE_NAME,
                "DELETE FROM TMP_PERSON WHERE pid = ?",
                TEST_PID);
    }

    private static void insertPersonViaDml() {
        IgniteSqlScriptRunner.executeUpdate(
                ignite,
                CACHE_NAME,
                """
                        INSERT INTO TMP_PERSON (pid, name, dep_id, age)
                        VALUES (?, ?, ?, ?)
                        """,
                TEST_PID,
                TEST_NAME,
                TEST_DEP_ID,
                TEST_AGE);
    }

    private static WorkerResult runSqlRowLayoutCachePutWorkerScenario() {
        Transaction tx = null;
        try {
            tx = ignite.transactions().txStart(
                    TransactionConcurrency.PESSIMISTIC,
                    TransactionIsolation.READ_COMMITTED);

            IgniteCache<BigDecimal, BinaryObject> cache = ignite.cache(CACHE_NAME).withKeepBinary();

            BinaryObject value = ignite.binary().builder(VALUE_TYPE)
                    .setField("name", TEST_NAME)
                    .setField("dep_id", TEST_DEP_ID)
                    .setField("age", TEST_AGE)
                    .build();

            cache.put(TEST_PID, value);

            boolean rowFound = matchesExpectedRow(queryPersonByPidAndName());
            Boolean removed = cache.invoke(TEST_PID, new RemoveDecimalKeyProcessor());
            boolean deleteSucceeded = Boolean.TRUE.equals(removed);

            tx.rollback();
            return new WorkerResult(rowFound, deleteSucceeded);
        } catch (Exception e) {
            if (tx != null) {
                tx.rollback();
            }
            throw new RuntimeException(e);
        }
    }

    private static boolean matchesExpectedRow(List<List<?>> selected) {
        if (selected.isEmpty()) {
            return false;
        }
        return selected.size() == 1
                && toBigDecimal(selected.get(0).get(0)).compareTo(TEST_PID) == 0
                && TEST_NAME.equals(selected.get(0).get(1));
    }

    private static void assertTableEmptyAfterCommittedReads() {
        List<List<?>> rows = IgniteSqlScriptRunner.executeQuery(
                ignite,
                CACHE_NAME,
                """
                        SELECT pid, name, dep_id, age
                        FROM TMP_PERSON
                        WHERE pid = ? AND name = ?
                        """,
                TEST_PID,
                TEST_NAME);
        assertThat(rows).isEmpty();
    }

    private static List<List<?>> queryPersonByPidAndName() {
        return IgniteSqlScriptRunner.executeQuery(
                ignite,
                CACHE_NAME,
                """
                        SELECT pid, name, dep_id, age
                        FROM TMP_PERSON
                        WHERE pid = ? AND name = ?
                        """,
                TEST_PID,
                TEST_NAME);
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    private static final class PutBinaryKeyValueProcessor
            implements CacheEntryProcessor<BinaryObject, BinaryObject, Void> {

        @Override
        public Void process(MutableEntry<BinaryObject, BinaryObject> entry, Object... arguments) {
            entry.setValue((BinaryObject) arguments[0]);
            return null;
        }
    }

    private static final class RemoveBinaryKeyProcessor
            implements CacheEntryProcessor<BinaryObject, BinaryObject, Boolean> {

        @Override
        public Boolean process(MutableEntry<BinaryObject, BinaryObject> entry, Object... arguments) {
            if (!entry.exists()) {
                return false;
            }
            entry.remove();
            return true;
        }
    }

    private static final class RemoveDecimalKeyProcessor
            implements CacheEntryProcessor<BigDecimal, BinaryObject, Boolean> {

        @Override
        public Boolean process(MutableEntry<BigDecimal, BinaryObject> entry, Object... arguments) {
            if (!entry.exists()) {
                return false;
            }
            entry.remove();
            return true;
        }
    }
}
