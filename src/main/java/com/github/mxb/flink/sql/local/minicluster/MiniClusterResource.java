package com.github.mxb.flink.sql.local.minicluster;

import com.github.mxb.flink.sql.local.command.LocalExecutorConstants;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.client.program.ClusterClient;
import org.apache.flink.client.program.MiniClusterClient;
import org.apache.flink.configuration.*;
import org.apache.flink.runtime.akka.AkkaUtils;
import org.apache.flink.runtime.minicluster.JobExecutorService;
import org.apache.flink.runtime.minicluster.MiniCluster;
import org.apache.flink.runtime.minicluster.MiniClusterConfiguration;
import org.apache.flink.streaming.util.TestStreamEnvironment;
import org.apache.flink.test.util.TestEnvironment;
import org.apache.flink.util.ExceptionUtils;
import org.apache.flink.util.FlinkRuntimeException;
import org.apache.flink.util.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Starts a Flink mini cluster as a resource and registers the respective .
 * ExecutionEnvironment and StreamExecutionEnvironment.
 *
 * @author moxianbin
 * @since 2020/4/10 17:36
 */
public class MiniClusterResource {

    private static final Logger LOG = LoggerFactory.getLogger(MiniClusterResource.class);

    protected static final long TASK_MANAGER_MEMORY_SIZE = 100;

    public static final String CODEBASE_KEY = "codebase";

    public static final String NEW_CODEBASE = "new";

    private final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final MiniClusterResourceConfiguration miniClusterResourceConfiguration;

    private final MiniClusterType miniClusterType;

    //mini Cluster
    private JobExecutorService jobExecutorService;

    private final boolean enableClusterClient;

    private ClusterClient<?> clusterClient;

    private Configuration restClusterClientConfig;

    private int numberSlots = -1;

    private TestEnvironment executionEnvironment;

    private int webUIPort = -1;

    private static final int DEFAULT_NUM_TMS = 8;
    private static final int DEFAULT_NUM_SLOTS_PER_TM = 8;

    public static MiniClusterResource getDefaultInstance() {
        return new MiniClusterResource(
                new MiniClusterResource.MiniClusterResourceConfiguration(
                        getConfig(), DEFAULT_NUM_TMS, DEFAULT_NUM_TMS
                ),
                true
        );
    }

    private static Configuration getConfig(int localTmNum, int localTmPerSlotsNum) {
        Configuration config = new Configuration();
        config.setLong(String.valueOf(TaskManagerOptions.MANAGED_MEMORY_SIZE), 4 * 1024 * 1024);
        config.setInteger(ConfigConstants.LOCAL_NUMBER_TASK_MANAGER, localTmNum);
        config.setInteger(TaskManagerOptions.NUM_TASK_SLOTS, localTmPerSlotsNum);
        config.setBoolean(WebOptions.SUBMIT_ENABLE, false);
        return config;
    }

    public static Configuration getConfig() {
        Configuration config = new Configuration();
        config.setLong(String.valueOf(TaskManagerOptions.MANAGED_MEMORY_SIZE), 4 * 1024 * 1024);
        config.setInteger(ConfigConstants.LOCAL_NUMBER_TASK_MANAGER, DEFAULT_NUM_TMS);
        config.setInteger(TaskManagerOptions.NUM_TASK_SLOTS, DEFAULT_NUM_SLOTS_PER_TM);
        config.setBoolean(WebOptions.SUBMIT_ENABLE, false);
        return config;
    }

    public MiniClusterResource(final MiniClusterResourceConfiguration miniClusterResourceConfiguration) {
        this(miniClusterResourceConfiguration, true);
    }

    // A bridge method for two MiniClusterResourceConfigurations.
    // Should remove when upgrade to newest version.
    public MiniClusterResource(final org.apache.flink.test.util.MiniClusterResourceConfiguration
                                       miniClusterResourceConfiguration) {
        this(new MiniClusterResourceConfiguration(miniClusterResourceConfiguration.getConfiguration(),
                miniClusterResourceConfiguration.getNumberTaskManagers(),
                miniClusterResourceConfiguration.getNumberSlotsPerTaskManager()), true);
    }

    public MiniClusterResource(
            final MiniClusterResourceConfiguration miniClusterResourceConfiguration,
            final MiniClusterType miniClusterType) {
        this(miniClusterResourceConfiguration, miniClusterType, false);
    }

    public MiniClusterResource(
            final MiniClusterResourceConfiguration miniClusterResourceConfiguration,
            final boolean enableClusterClient) {
        this(
                miniClusterResourceConfiguration,
                MiniClusterType.NEW, //Objects.equals(NEW_CODEBASE, System.getProperty(CODEBASE_KEY)) ? MiniClusterType.NEW : MiniClusterType.LEGACY,
                enableClusterClient);
    }

    private MiniClusterResource(
            final MiniClusterResourceConfiguration miniClusterResourceConfiguration,
            final MiniClusterType miniClusterType,
            final boolean enableClusterClient) {
        this.miniClusterResourceConfiguration = Preconditions.checkNotNull(miniClusterResourceConfiguration);
        this.miniClusterType = Preconditions.checkNotNull(miniClusterType);
        this.enableClusterClient = enableClusterClient;
    }

    public MiniClusterType getMiniClusterType() {
        return miniClusterType;
    }

    public int getNumberSlots() {
        return numberSlots;
    }

    public ClusterClient<?> getClusterClient() {
        if (!enableClusterClient) {
            // this check is technically only necessary for legacy clusters
            // we still fail here to keep the behaviors in sync
            throw new IllegalStateException("To use the client you must enable it with the constructor.");
        }

        return clusterClient;
    }

    public Configuration getClientConfiguration() {
        return restClusterClientConfig;
    }

    public TestEnvironment getTestEnvironment() {
        return executionEnvironment;
    }

    public int getWebUIPort() {
        return webUIPort;
    }

    public void startCluster() throws Exception {
        synchronized (MiniClusterResource.class) {
            if (null == jobExecutorService) {
                temporaryFolder.create();
                startJobExecutorService(miniClusterType);

                numberSlots = miniClusterResourceConfiguration.getNumberSlotsPerTaskManager() * miniClusterResourceConfiguration.getNumberTaskManagers();

                executionEnvironment = new TestEnvironment(jobExecutorService, numberSlots, false);
                executionEnvironment.setAsContext();
                TestStreamEnvironment.setAsContext(jobExecutorService, numberSlots);
            }
        }
    }

    public void shutdownCluster() {
        temporaryFolder.delete();

        TestStreamEnvironment.unsetAsContext();
        TestEnvironment.unsetAsContext();

        Exception exception = null;

        if (clusterClient != null) {
            try {
                clusterClient.shutdown();
            } catch (Exception e) {
                exception = e;
            }
        }

        clusterClient = null;

        final CompletableFuture<?> terminationFuture = jobExecutorService.closeAsync();

        try {
            terminationFuture.get(
                    miniClusterResourceConfiguration.getShutdownTimeout().toMilliseconds(),
                    TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            exception = ExceptionUtils.firstOrSuppressed(e, exception);
        }

        jobExecutorService = null;

        if (exception != null) {
            LOG.warn("Could not properly shut down the MiniClusterResource.", exception);
        }
    }

    private void startJobExecutorService(MiniClusterType miniClusterType) throws Exception {
        switch (miniClusterType) {
            case NEW:
                startMiniCluster();
                break;
            default:
                throw new FlinkRuntimeException("Unknown MiniClusterType " + miniClusterType + '.');
        }
    }

    private void startMiniCluster() throws Exception {
        final Configuration configuration = miniClusterResourceConfiguration.getConfiguration();
        configuration.setString(CoreOptions.TMP_DIRS, temporaryFolder.newFolder().getAbsolutePath());

        // we need to set this since a lot of test expect this because TestBaseUtils.startCluster()
        // enabled this by default
        if (!configuration.contains(CoreOptions.FILESYTEM_DEFAULT_OVERRIDE)) {
            configuration.setBoolean(CoreOptions.FILESYTEM_DEFAULT_OVERRIDE, true);
        }

        if (!configuration.contains(TaskManagerOptions.MANAGED_MEMORY_SIZE)) {
            configuration.setLong(String.valueOf(TaskManagerOptions.MANAGED_MEMORY_SIZE), TASK_MANAGER_MEMORY_SIZE);
        }

        // set rest port to 0 to avoid clashes with concurrent MiniClusters
        configuration.setInteger(RestOptions.PORT, 0);

        final MiniClusterConfiguration miniClusterConfiguration = new MiniClusterConfiguration.Builder()
                .setConfiguration(configuration)
                .setNumTaskManagers(miniClusterResourceConfiguration.getNumberTaskManagers())
                .setNumSlotsPerTaskManager(miniClusterResourceConfiguration.getNumberSlotsPerTaskManager())
                .build();

        final MiniCluster miniCluster = new MiniCluster(miniClusterConfiguration);

        miniCluster.start();

        // update the port of the rest endpoint
        configuration.setInteger(RestOptions.PORT, miniCluster.getRestAddress().get().getPort());

        jobExecutorService = miniCluster;
        if (enableClusterClient) {
            clusterClient = new MiniClusterClient(configuration, miniCluster);
        }
        Configuration restClientConfig = new Configuration();
        restClientConfig.setString(JobManagerOptions.ADDRESS, miniCluster.getRestAddress().get().getHost());
        restClientConfig.setInteger(RestOptions.PORT, miniCluster.getRestAddress().get().getPort());
        this.restClusterClientConfig = new UnmodifiableConfiguration(restClientConfig);

        webUIPort = miniCluster.getRestAddress().get().getPort();
    }

    /**
     * Mini cluster resource configuration object.
     */
    public static class MiniClusterResourceConfiguration {
        private final Configuration configuration;

        private final int numberTaskManagers;

        private final int numberSlotsPerTaskManager;

        private final Time shutdownTimeout;

        public MiniClusterResourceConfiguration(int numberTaskManagers,
                                                int numberSlotsPerTaskManager) {
            this(getConfig(numberTaskManagers, numberSlotsPerTaskManager), numberTaskManagers, numberSlotsPerTaskManager);
        }

        public MiniClusterResourceConfiguration(
                Configuration configuration,
                int numberTaskManagers,
                int numberSlotsPerTaskManager) {
            this(
                    configuration,
                    numberTaskManagers,
                    numberSlotsPerTaskManager,
                    AkkaUtils.getTimeoutAsTime(configuration));
        }

        public MiniClusterResourceConfiguration(
                Configuration configuration,
                int numberTaskManagers,
                int numberSlotsPerTaskManager,
                Time shutdownTimeout) {
            this.configuration = Preconditions.checkNotNull(configuration);
            this.numberTaskManagers = numberTaskManagers;
            this.numberSlotsPerTaskManager = numberSlotsPerTaskManager;
            this.shutdownTimeout = Preconditions.checkNotNull(shutdownTimeout);
        }

        public Configuration getConfiguration() {
            return configuration;
        }

        public int getNumberTaskManagers() {
            return numberTaskManagers;
        }

        public int getNumberSlotsPerTaskManager() {
            return numberSlotsPerTaskManager;
        }

        public Time getShutdownTimeout() {
            return shutdownTimeout;
        }

        public Map<String, String> toProperties() {
            Map<String, String> properties = new HashMap<>();
            properties.put(LocalExecutorConstants.LOCAL_TM_NUM_KEY, String.valueOf(this.numberTaskManagers));
            properties.put(LocalExecutorConstants.LOCAL_NUM_SLOTS_PER_TM_KEY, String.valueOf(this.numberSlotsPerTaskManager));

            return properties;
        }

    }

    // ---------------------------------------------
    // Enum definitions
    // ---------------------------------------------

    /**
     * Type of the mini cluster to start.
     */
    public enum MiniClusterType {
        LEGACY,
        NEW
    }
}
