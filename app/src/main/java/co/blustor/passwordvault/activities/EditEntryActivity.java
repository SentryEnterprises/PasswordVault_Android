package co.blustor.passwordvault.activities;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import com.basgeekball.awesomevalidation.AwesomeValidation;
import com.basgeekball.awesomevalidation.utility.RegexTemplate;

import java.util.UUID;

import co.blustor.passwordvault.R;
import co.blustor.passwordvault.database.Vault;
import co.blustor.passwordvault.database.VaultEntry;
import co.blustor.passwordvault.database.VaultGroup;
import co.blustor.passwordvault.extensions.LockingActivity;
import co.blustor.passwordvault.sync.SyncDialogFragment;
import co.blustor.passwordvault.sync.SyncManager;

import static com.basgeekball.awesomevalidation.ValidationStyle.BASIC;

public class EditEntryActivity extends LockingActivity implements SyncDialogFragment.SyncInterface {
    private static final String TAG = "EditEntryActivity";
    private final AwesomeValidation mAwesomeValidation = new AwesomeValidation(BASIC);
    private VaultGroup mGroup = null;
    private VaultEntry mEntry = null;
    private EditText mTitleEditText = null;
    private EditText mUsernameEditText = null;
    private EditText mPasswordEditText = null;
    private EditText mUrlEditText = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_entry);

        // Validation

        mAwesomeValidation.addValidation(this, R.id.edittext_title, RegexTemplate.NOT_EMPTY, R.string.error_empty);

        // Views

        mTitleEditText = (EditText) findViewById(R.id.edittext_title);
        mUsernameEditText = (EditText) findViewById(R.id.edittext_username);
        mPasswordEditText = (EditText) findViewById(R.id.edittext_password);
        mUrlEditText = (EditText) findViewById(R.id.edittext_url);

        // Load

        UUID groupUUID = (UUID) getIntent().getSerializableExtra("groupUUID");
        UUID uuid = (UUID) getIntent().getSerializableExtra("uuid");

        try {
            Vault vault = Vault.getInstance();

            mGroup = vault.getGroupByUUID(groupUUID);
            mEntry = mGroup.getEntry(uuid);
            load();
        } catch (Vault.GroupNotFoundException | VaultGroup.EntryNotFoundException e) {
            e.printStackTrace();
            supportFinishAfterTransition();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_edit_entry, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_delete) {
            delete();
        } else if (id == R.id.action_save) {
            save();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setMessage("Close without saving?")
                .setPositiveButton("Close", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        supportFinishAfterTransition();
                    }

                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void load() {
        setTitle("Edit entry");

        mTitleEditText.setText(mEntry.getTitle());
        mUsernameEditText.setText(mEntry.getUsername());
        mPasswordEditText.setText(mEntry.getPassword());
        mUrlEditText.setText(mEntry.getUrl());
    }

    private void delete() {
         new AlertDialog.Builder(this)
                .setMessage("Are you sure you want to delete this entry?")
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                })
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mGroup.removeEntry(mEntry.getUUID());
                        save();
                    }
                }).show();
    }

    private void save() {
        if (mAwesomeValidation.validate()) {
            mEntry.setTitle(mTitleEditText.getText().toString());
            mEntry.setUsername(mUsernameEditText.getText().toString());
            mEntry.setPassword(mPasswordEditText.getText().toString());
            mEntry.setUrl(mUrlEditText.getText().toString());

            Vault vault = Vault.getInstance();

            SyncDialogFragment syncDialogFragment = new SyncDialogFragment();

            Bundle args = new Bundle();
            args.putSerializable("type", SyncManager.SyncType.WRITE);
            args.putSerializable("password", vault.getPassword());

            syncDialogFragment.setArguments(args);
            syncDialogFragment.show(getFragmentManager(), "dialog");
        }
    }

    @Override
    public void syncComplete(UUID uuid) {
        supportFinishAfterTransition();
    }
}
