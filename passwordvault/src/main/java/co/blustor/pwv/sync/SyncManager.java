package co.blustor.pwv.sync;

import android.content.Context;
import android.support.annotation.NonNull;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import co.blustor.gatekeepersdk.devices.GKBluetoothCard;
import co.blustor.gatekeepersdk.devices.GKCard;
import co.blustor.pwv.database.Translator;
import co.blustor.pwv.database.Vault;
import co.blustor.pwv.database.VaultGroup;
import de.slackspace.openkeepass.KeePassDatabase;
import de.slackspace.openkeepass.domain.Group;
import de.slackspace.openkeepass.domain.KeePassFile;
import de.slackspace.openkeepass.domain.KeePassFileBuilder;
import de.slackspace.openkeepass.exception.KeePassDatabaseUnreadableException;

public class SyncManager {
    private static final String CARD_NAME = "CYBERGATE";
    private static final String VAULT_PATH = "/passwordvault/db.kdbx";
    private static final DeferredObject<Void, Exception, SyncStatus> syncStatus = new DeferredObject<>();
    @NonNull
    private static SyncStatus lastSyncStatus = SyncStatus.SYNCED;

    public static synchronized Promise<VaultGroup, SyncManagerException, SyncStatus> getRoot(@NonNull final Context context, @NonNull final String password) {
        final DeferredObject<VaultGroup, SyncManagerException, SyncStatus> task = new DeferredObject<>();
        new Thread() {
            @Override
            public void run() {
                task.notify(SyncStatus.SAVING);

                GKBluetoothCard card = new GKBluetoothCard(CARD_NAME, context.getCacheDir());

                try {
                    try {
                        card.connect();

                        if (card.getConnectionState() == GKCard.ConnectionState.CARD_NOT_PAIRED) {
                            throw new SyncManagerException("Card is not paired. Please pair your card with your phone.");
                        } else if (card.getConnectionState() == GKCard.ConnectionState.BLUETOOTH_DISABLED) {
                            throw new SyncManagerException("Bluetooth is not enabled. Please enable bluetooth under settings.");
                        }

                        GKCard.Response response = card.get(VAULT_PATH);
                        int status = response.getStatus();

                        if (status == 226) {
                            File file = response.getDataFile();

                            try {
                                task.notify(SyncStatus.DECRYPTING);
                                KeePassFile keePassFile = KeePassDatabase.getInstance(file).openDatabase(password);

                                Group keePassRoot = keePassFile.getRoot().getGroups().get(0);
                                VaultGroup group = Translator.importKeePass(keePassRoot);

                                Vault vault = Vault.getInstance();
                                vault.setRoot(group);
                                vault.setPassword(password);

                                task.resolve(group);
                            } catch (KeePassDatabaseUnreadableException e) {
                                task.reject(new SyncManagerException("Database password invalid."));
                            }
                        } else if (status == 550) {
                            throw new SyncManagerException("Database not found on card.");
                        } else {
                            throw new SyncManagerException("Card status: " + status);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new SyncManagerException("Unable to connect to card.");
                    }
                } catch (SyncManagerException e) {
                    task.reject(e);
                }

                try {
                    card.disconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
        return task.promise();
    }

    public static synchronized Promise<VaultGroup, SyncManagerException, SyncStatus> setRoot(@NonNull final Context context, final String password) {
        final DeferredObject<VaultGroup, SyncManagerException, SyncStatus> task = new DeferredObject<>();
        new Thread() {
            @Override
            public void run() {
                task.notify(SyncStatus.ENCRYPTING);
                syncStatus.notify(SyncStatus.ENCRYPTING);
                lastSyncStatus = SyncStatus.ENCRYPTING;

                GKBluetoothCard card = new GKBluetoothCard(CARD_NAME, context.getCacheDir());

                try {
                    try {
                        Vault vault = Vault.getInstance();
                        VaultGroup rootGroup = vault.getRoot();

                        if (rootGroup == null) {
                            throw new SyncManagerException("Vault is empty.");
                        }

                        Group group = Translator.exportKeePass(vault.getRoot());

                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

                        KeePassFile keePassFile = new KeePassFileBuilder("passwords").addTopGroups(group).build();
                        KeePassDatabase.write(keePassFile, password, byteArrayOutputStream);
                        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());

                        task.notify(SyncStatus.SAVING);
                        syncStatus.notify(SyncStatus.SAVING);
                        lastSyncStatus = SyncStatus.SAVING;

                        card.connect();

                        if (card.getConnectionState() == GKCard.ConnectionState.CARD_NOT_PAIRED) {
                            throw new SyncManagerException("Card is not paired.");
                        } else if (card.getConnectionState() == GKCard.ConnectionState.BLUETOOTH_DISABLED) {
                            throw new SyncManagerException("Bluetooth is not enabled.");
                        }

                        GKCard.Response response = card.put(VAULT_PATH, byteArrayInputStream);

                        int status = response.getStatus();
                        if (status == 226) {
                            vault.setPassword(password);

                            card.finalize(VAULT_PATH);

                            task.resolve(rootGroup);
                            syncStatus.notify(SyncStatus.SYNCED);
                            lastSyncStatus = SyncStatus.SYNCED;
                        } else {
                            throw new SyncManagerException("Card status: " + status);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new SyncManagerException(e.getMessage());
                    }
                } catch (SyncManagerException e) {
                    syncStatus.notify(SyncStatus.FAILED);
                    lastSyncStatus = SyncStatus.FAILED;
                    task.reject(e);
                }

                try {
                    card.disconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
        return task.promise();
    }

    public static synchronized Promise<Boolean, SyncManagerException, SyncStatus> exists(@NonNull final Context context) {
        final DeferredObject<Boolean, SyncManagerException, SyncStatus> task = new DeferredObject<>();
        new Thread() {
            @Override
            public void run() {
                GKBluetoothCard card = new GKBluetoothCard(CARD_NAME, context.getCacheDir());

                try {
                    try {
                        GKCard.Response response = card.list("/passwordvault");
                        String[] lines = response.readDataFile().split("\\r?\\n");

                        Boolean isFound = false;
                        for (String line : lines) {
                            if (line.endsWith(" db.kdbx")) {
                                isFound = true;
                            }
                        }

                        task.resolve(isFound);
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new SyncManagerException(e.getMessage());
                    }
                } catch (SyncManagerException e) {
                    task.reject(e);
                }

                try {
                    card.disconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
        return task.promise();
    }

    public static Promise<Void, Exception, SyncStatus> getWriteStatusPromise() {
        return syncStatus.promise();
    }

    @NonNull
    public static SyncStatus getLastWriteStatus() {
        return lastSyncStatus;
    }

    public enum SyncType {
        READ, WRITE
    }

    public enum SyncStatus {
        SAVING, ENCRYPTING, DECRYPTING, FAILED, SYNCED
    }

    public static class SyncManagerException extends Exception {
        SyncManagerException(String messasge) {
            super(messasge);
        }
    }
}
