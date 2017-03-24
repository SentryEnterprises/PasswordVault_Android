package co.blustor.passwordvault.sync;

import android.content.Context;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import co.blustor.gatekeepersdk.devices.GKBluetoothCard;
import co.blustor.gatekeepersdk.devices.GKCard;
import co.blustor.passwordvault.database.Translator;
import co.blustor.passwordvault.database.Vault;
import co.blustor.passwordvault.database.VaultGroup;
import de.slackspace.openkeepass.KeePassDatabase;
import de.slackspace.openkeepass.domain.Group;
import de.slackspace.openkeepass.domain.KeePassFile;
import de.slackspace.openkeepass.domain.KeePassFileBuilder;
import de.slackspace.openkeepass.exception.KeePassDatabaseUnreadableException;

public class SyncManager {
    private static String CARD_NAME = "CYBERGATE";
    private static String VAULT_PATH = "/passwordvault/vault.kdbx";

    public static Promise<VaultGroup, Exception, SyncStatus> getRoot(final Context context, final String password) {
        final DeferredObject<VaultGroup, Exception, SyncStatus> deferredObject = new DeferredObject<>();
        new Thread() {
            @Override
            public void run() {
                GKBluetoothCard card = new GKBluetoothCard(CARD_NAME, context.getCacheDir());

                try {
                    card.connect();

                    deferredObject.notify(SyncStatus.TRANSFERRING);

                    GKCard.Response response = card.get(VAULT_PATH);
                    int status = response.getStatus();

                    if (status == 226) {
                        File file = response.getDataFile();

                        try {
                            deferredObject.notify(SyncStatus.DECRYPTING);
                            KeePassFile keePassFile = KeePassDatabase.getInstance(file).openDatabase(password);

                            Group keePassRoot = keePassFile.getRoot().getGroups().get(0);
                            VaultGroup group = Translator.importKeePass(keePassRoot);

                            Vault vault = Vault.getInstance();
                            vault.setRoot(group);
                            vault.setPassword(password);

                            deferredObject.resolve(group);
                        } catch (KeePassDatabaseUnreadableException e) {
                            deferredObject.reject(new SyncManagerException("Database password invalid."));
                        }
                    } else if (status == 550) {
                        deferredObject.reject(new SyncManagerException("Database not found on card."));
                    } else {
                        deferredObject.reject(new SyncManagerException("Card status: " + status));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    deferredObject.reject(new SyncManagerException("Unable to connect to card."));
                }

                try {
                    card.disconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
        return deferredObject.promise();
    }

    public static Promise<VaultGroup, Exception, SyncStatus> setRoot(final Context context, final String password) {
        final DeferredObject<VaultGroup, Exception, SyncStatus> deferredObject = new DeferredObject<>();
        new Thread() {
            @Override
            public void run() {
                deferredObject.notify(SyncStatus.ENCRYPTING);

                GKBluetoothCard card = new GKBluetoothCard(CARD_NAME, context.getCacheDir());

                try {
                    Vault vault = Vault.getInstance();
                    VaultGroup rootGroup = vault.getRoot();

                    Group group = Translator.exportKeePass(vault.getRoot());

                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

                    KeePassFile keePassFile = new KeePassFileBuilder("passwords").addTopGroups(group).build();
                    KeePassDatabase.write(keePassFile, password, byteArrayOutputStream);
                    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());

                    deferredObject.notify(SyncStatus.TRANSFERRING);

                    GKCard.Response response = card.put(VAULT_PATH, byteArrayInputStream);

                    int status = response.getStatus();
                    if (status == 226) {
                        vault.setPassword(password);

                        card.finalize(VAULT_PATH);

                        deferredObject.resolve(rootGroup);
                    } else {
                        deferredObject.reject(new SyncManagerException("Card status: " + status));
                    }
                } catch (Vault.GroupNotFoundException e) {
                    deferredObject.reject(new SyncManagerException("Vault is empty."));
                } catch (IOException e) {
                    deferredObject.reject(new SyncManagerException("Unable to connect to card."));
                }

                try {
                    card.disconnect();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
        return deferredObject.promise();
    }

    public enum SyncType {
        READ, WRITE
    }

    enum SyncStatus {
        TRANSFERRING, ENCRYPTING, DECRYPTING
    }

    private static class SyncManagerException extends Exception {
        SyncManagerException(String messasge) {
            super(messasge);
        }
    }
}