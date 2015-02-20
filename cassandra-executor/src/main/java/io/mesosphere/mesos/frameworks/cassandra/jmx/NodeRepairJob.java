/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.mesosphere.mesos.frameworks.cassandra.jmx;

import io.mesosphere.mesos.frameworks.cassandra.CassandraTaskProtos;
import org.apache.cassandra.service.ActiveRepairService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationListener;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NodeRepairJob extends AbstractKeyspacesJob implements NotificationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeRepairJob.class);

    private final Map<Integer, String> commandToKeyspace = new HashMap<>();
    private final Map<String, CassandraTaskProtos.KeyspaceRepairStatus> keyspaceStatus = new HashMap<>();
    private long finishedTimestamp;

    private volatile long keyspaceStartedAt;

    public NodeRepairJob() {
    }

    public boolean start(JmxConnect jmxConnect) {
        if (!super.start(jmxConnect))
            return false;

        jmxConnect.getStorageServiceProxy().addNotificationListener(this, null, null);

        LOGGER.info("Initiated repair job for keyspaces {}", keyspaces);

        return true;
    }

    public boolean isFinished() {
        return finishedTimestamp != 0L;
    }

    public long getFinishedTimestamp() {
        return finishedTimestamp;
    }

    public List<String> getRemainingKeyspaces() {
        return keyspaces;
    }

    public Map<String, CassandraTaskProtos.KeyspaceRepairStatus> getKeyspaceStatus() {
        return keyspaceStatus;
    }

    public boolean repairNextKeyspace() {
        while (true) {
            if (keyspaces.isEmpty()) {
                finished();
                return false;
            }

            String keyspace = keyspaces.remove(0);

            // equivalent to 'nodetool repair' WITHOUT --partitioner-range, --full, --in-local-dc, --sequential
            int commandNo = jmxConnect.getStorageServiceProxy().forceRepairAsync(keyspace, false, false, false, false);
            if (commandNo == 0) {
                LOGGER.info("Nothing to repair for keyspace {}", keyspace);
                continue;
            }

            commandToKeyspace.put(commandNo, keyspace);

            LOGGER.info("Submitted repair for keyspace {} with cmd#{}", keyspace, commandNo);
            return true;
        }
    }

    private void finished() {
        try {
            finishedTimestamp = System.currentTimeMillis();
            long duration = System.currentTimeMillis() - startTimestamp;
            LOGGER.info("Repair job finished in {} seconds : {}", duration / 1000L, keyspaceStatus);
            jmxConnect.getStorageServiceProxy().removeNotificationListener(this);
            close();
        } catch (ListenerNotFoundException ignored) {
            // ignore this
        }
    }

    @Override
    public void handleNotification(Notification notification, Object handback) {
        if (!"repair".equals(notification.getType()))
            return;

        int[] result = (int[]) notification.getUserData();
        int repairCommandNo = result[0];
        ActiveRepairService.Status status = ActiveRepairService.Status.values()[result[1]];

        String keyspace = commandToKeyspace.get(repairCommandNo);
        LOGGER.info("Received notification about repair for keyspace {} with cmd#{} with status {}", keyspace, repairCommandNo, status);

        switch (status) {
            case STARTED:
                keyspaceStartedAt = System.currentTimeMillis();
                break;
            case SESSION_SUCCESS:
            case SESSION_FAILED:
                break;
            case FINISHED:

                // TODO possible race if this notification is received _before_ commandToKeyspace.put() is executed in repairNextKeyspace()

                keyspaceStatus.put(keyspace, CassandraTaskProtos.KeyspaceRepairStatus.newBuilder()
                        .setDuration(System.currentTimeMillis() - keyspaceStartedAt)
                        .setStatus(status.name())
                        .setKeyspace(keyspace)
                        .build());

                repairNextKeyspace();

                break;
        }
    }

}
