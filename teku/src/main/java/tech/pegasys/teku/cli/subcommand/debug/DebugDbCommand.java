/*
 * Copyright ConsenSys Software Inc., 2022
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.cli.subcommand.debug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.BeaconNodeDataOptions;
import tech.pegasys.teku.cli.options.Eth2NetworkOptions;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DepositStorage;
import tech.pegasys.teku.storage.server.StorageConfiguration;
import tech.pegasys.teku.storage.server.VersionedDatabaseFactory;
import tech.pegasys.teku.storage.store.StoreBuilder;
import tech.pegasys.teku.storage.store.UpdatableStore;

@Command(
    name = "db",
    description = "Debugging tools for inspecting the contents of the database",
    showDefaultValues = true,
    abbreviateSynopsis = true,
    mixinStandardHelpOptions = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class DebugDbCommand implements Runnable {
  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  @Command(
      name = "get-deposits",
      description = "List the ETH1 deposit information stored in the database",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int getDeposits(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions)
      throws Exception {
    try (final YamlEth1EventsChannel eth1EventsChannel = new YamlEth1EventsChannel(System.out);
        final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {
      final DepositStorage depositStorage = DepositStorage.create(eth1EventsChannel, database);
      depositStorage.replayDepositEvents().join();
    }
    return 0;
  }

  @Command(
      name = "get-finalized-state",
      description = "Get the finalized state, if available, as SSZ",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int getFinalizedState(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              required = true,
              names = {"--output", "-o"},
              description = "File to write state to")
          final Path outputFile,
      @Option(
              required = true,
              names = {"--slot", "-s"},
              description =
                  "The slot to retrieve the state for. If unavailable the closest available state will be returned")
          final long slot)
      throws Exception {
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {
      return writeState(
          outputFile, database.getLatestAvailableFinalizedState(UInt64.valueOf(slot)));
    }
  }

  @Command(
      name = "get-finalized-state-indices",
      description = "Display the slots of finalized states that are stored.",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int getFinalizedStateIndices(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              required = true,
              names = {"--start-slot"},
              defaultValue = "0",
              description = "The start index of the range to display")
          final long startSlot,
      @Option(
              required = true,
              names = {"--end-slot"},
              defaultValue = "9223372036854775807",
              description = "The end index of the range to display")
          final long endSlot)
      throws Exception {
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {
      System.out.println("Searching for finalized states in the finalized store");
      try (Stream<UInt64> stream =
          database.streamFinalizedStateSlots(UInt64.valueOf(startSlot), UInt64.valueOf(endSlot))) {
        stream.forEach(System.out::println);
      }
    }
    return 0;
  }

  @Command(
      name = "get-latest-finalized-state",
      description = "Get the latest finalized state, if available, as SSZ",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int getLatestFinalizedState(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions,
      @Option(
              required = true,
              names = {"--output", "-o"},
              description = "File to write state to")
          final Path outputFile,
      @Option(
              names = {"--block-output"},
              description = "File to write the block matching the latest finalized state to")
          final Path blockOutputFile)
      throws Exception {
    final AsyncRunnerFactory asyncRunnerFactory =
        AsyncRunnerFactory.createDefault(
            new MetricTrackingExecutorFactory(new NoOpMetricsSystem()));
    final AsyncRunner asyncRunner = asyncRunnerFactory.create("async", 1);
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {
      final Optional<AnchorPoint> finalizedAnchor =
          database
              .createMemoryStore()
              .map(
                  storeData ->
                      StoreBuilder.create()
                          .onDiskStoreData(storeData)
                          .metricsSystem(new NoOpMetricsSystem())
                          .specProvider(eth2NetworkOptions.getNetworkConfiguration().getSpec())
                          .blockProvider(BlockProvider.NOOP)
                          .stateProvider(StateAndBlockSummaryProvider.NOOP)
                          .build())
              .map(UpdatableStore::getLatestFinalized);
      int result = writeState(outputFile, finalizedAnchor.map(AnchorPoint::getState));
      if (result == 0 && blockOutputFile != null) {
        final Optional<SignedBeaconBlock> finalizedBlock =
            finalizedAnchor.flatMap(AnchorPoint::getSignedBeaconBlock);
        result = writeBlock(blockOutputFile, finalizedBlock);
      }
      return result;
    } finally {
      asyncRunner.shutdown();
    }
  }

  @Command(
      name = "get-column-counts",
      description = "Count entries in columns in storage tables",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public int getColumnCounts(
      @Mixin final BeaconNodeDataOptions beaconNodeDataOptions,
      @Mixin final Eth2NetworkOptions eth2NetworkOptions)
      throws Exception {
    try (final Database database = createDatabase(beaconNodeDataOptions, eth2NetworkOptions)) {
      database.getColumnCounts().forEach(this::printColumn);
    }
    return 0;
  }

  private void printColumn(final String label, final long count) {
    System.out.printf("%40s: %d%n", label, count);
  }

  private Database createDatabase(
      final BeaconNodeDataOptions beaconNodeDataOptions,
      final Eth2NetworkOptions eth2NetworkOptions) {
    final Spec spec = eth2NetworkOptions.getNetworkConfiguration().getSpec();
    final VersionedDatabaseFactory databaseFactory =
        new VersionedDatabaseFactory(
            new NoOpMetricsSystem(),
            DataDirLayout.createFrom(beaconNodeDataOptions.getDataConfig())
                .getBeaconDataDirectory(),
            Optional.empty(),
            StorageConfiguration.builder()
                .storeBlockExecutionPayloadSeparately(
                    beaconNodeDataOptions.isStoreBlockExecutionPayloadSeparately())
                .eth1DepositContract(
                    eth2NetworkOptions.getNetworkConfiguration().getEth1DepositContractAddress())
                .storeVotesEquivocation(
                    eth2NetworkOptions.getNetworkConfiguration().isEquivocatingIndicesEnabled())
                .specProvider(spec)
                .build());
    return databaseFactory.createDatabase();
  }

  private int writeState(final Path outputFile, final Optional<BeaconState> state) {
    if (state.isEmpty()) {
      System.err.println("No state available.");
      return 2;
    }
    try {
      Files.write(outputFile, state.get().sszSerialize().toArrayUnsafe());
    } catch (IOException e) {
      System.err.println("Unable to write state to " + outputFile + ": " + e.getMessage());
      return 1;
    }
    return 0;
  }

  private int writeBlock(final Path outputFile, final Optional<SignedBeaconBlock> block) {
    if (block.isEmpty()) {
      System.err.println("No block available.");
      return 2;
    }
    try {
      Files.write(outputFile, block.get().sszSerialize().toArrayUnsafe());
    } catch (IOException e) {
      System.err.println("Unable to write block to " + outputFile + ": " + e.getMessage());
      return 1;
    }
    return 0;
  }
}
