/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.email.activity.setup;

import android.app.ActionBar;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;

import com.android.email.R;
import com.android.emailcommon.utility.IntentUtilities;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider.EditSettingsExtras;
import com.android.mail.ui.settings.MailPreferenceActivity;
import com.android.mail.utils.Utils;

import java.util.List;

/**
 * Handles account preferences, using multi-pane arrangement when possible.
 *
 * This activity uses the following fragments:
 *   AccountSettingsFragment
 *   GeneralPreferences
 *   DebugFragment
 *
 */
public class AccountSettings extends MailPreferenceActivity {
    /*
     * Intent to open account settings for account=1
        adb shell am start -a android.intent.action.EDIT \
            -d '"content://ui.email.android.com/settings?ACCOUNT_ID=1"'
     */

    // Intent extras for our internal activity launch
    private static final String EXTRA_ENABLE_DEBUG = "AccountSettings.enable_debug";
    private static final String EXTRA_LOGIN_WARNING_FOR_ACCOUNT = "AccountSettings.for_account";
    private static final String EXTRA_LOGIN_WARNING_REASON_FOR_ACCOUNT =
            "AccountSettings.for_account_reason";
    // STOPSHIP: Do not ship with the debug menu allowed.
    private static final boolean DEBUG_MENU_ALLOWED = false;
    public static final String EXTRA_NO_ACCOUNTS = "AccountSettings.no_account";

    // Intent extras for launch directly from system account manager
    // NOTE: This string must match the one in res/xml/account_preferences.xml
    private static String INTENT_ACCOUNT_MANAGER_ENTRY;

    // Key codes used to open a debug settings fragment.
    private static final int[] SECRET_KEY_CODES = {
            KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_U,
            KeyEvent.KEYCODE_G
            };
    private int mSecretKeyCodeIndex = 0;

    // When the user taps "Email Preferences" 10 times in a row, we'll enable the debug settings.
    private int mNumGeneralHeaderClicked = 0;

    private boolean mShowDebugMenu;
    private Uri mFeedbackUri;
    private MenuItem mFeedbackMenuItem;

    /**
     * Create and return an intent to display (and edit) settings for a specific account, or -1
     * for any/all accounts.  If an account name string is provided, a warning dialog will be
     * displayed as well.
     */
    public static Intent createAccountSettingsIntent(final Context context, final long accountId,
            final String loginWarningAccountName, final String loginWarningReason) {
        final Uri.Builder b = IntentUtilities.createActivityIntentUrlBuilder(
                IntentUtilities.PATH_SETTINGS);
        IntentUtilities.setAccountId(b, accountId);
        final Intent i = new Intent(Intent.ACTION_EDIT, b.build());
        i.setPackage(context.getPackageName());
        if (loginWarningAccountName != null) {
            i.putExtra(EXTRA_LOGIN_WARNING_FOR_ACCOUNT, loginWarningAccountName);
        }
        if (loginWarningReason != null) {
            i.putExtra(EXTRA_LOGIN_WARNING_REASON_FOR_ACCOUNT, loginWarningReason);
        }
        return i;
    }

    @Override
    public Intent getIntent() {
        final Intent intent = super.getIntent();
        final long accountId = IntentUtilities.getAccountIdFromIntent(intent);
        if (accountId < 0) {
            return intent;
        }
        Intent modIntent = new Intent(intent);
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, AccountSettingsFragment.class.getCanonicalName());
        modIntent.putExtra(
                EXTRA_SHOW_FRAGMENT_ARGUMENTS,
                AccountSettingsFragment.buildArguments(
                        IntentUtilities.getAccountNameFromIntent(intent)));
        modIntent.putExtra(EXTRA_NO_HEADERS, true);
        return modIntent;
    }


    /**
     * Launch generic settings and pre-enable the debug preferences
     */
    public static void actionSettingsWithDebug(Context fromContext) {
        final Intent i = new Intent(fromContext, AccountSettings.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra(EXTRA_ENABLE_DEBUG, true);
        fromContext.startActivity(i);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent i = getIntent();
        if (savedInstanceState == null) {
            // If we are not restarting from a previous instance, we need to
            // figure out the initial prefs to show.  (Otherwise, we want to
            // continue showing whatever the user last selected.)
            if (INTENT_ACCOUNT_MANAGER_ENTRY == null) {
                INTENT_ACCOUNT_MANAGER_ENTRY = getString(R.string.intent_account_manager_entry);
            }
            if (INTENT_ACCOUNT_MANAGER_ENTRY.equals(i.getAction())) {
                // This case occurs if we're changing account settings from Settings -> Accounts.
                // We get an account object in the intent, but it's not actually useful to us since
                // it's always just the first account of that type. The user can't specify which
                // account they wish to view from within the settings UI, so just dump them at the
                // main screen.
                // android.accounts.Account acct = i.getParcelableExtra("account");
            } else if (i.hasExtra(EditSettingsExtras.EXTRA_FOLDER)) {
                launchMailboxSettings(i);
                return;
            } else if (i.hasExtra(EXTRA_NO_ACCOUNTS)) {
                final Intent setupIntent = AccountSetupFinal.actionNewAccountWithResultIntent(this);
                startActivity(setupIntent);
                finish();
                return;
            } else {
                // Otherwise, we're called from within the Email app and look for our extras
                final long accountId = IntentUtilities.getAccountIdFromIntent(i);
                if (accountId != -1) {
                    String loginWarningAccount = i.getStringExtra(EXTRA_LOGIN_WARNING_FOR_ACCOUNT);
                    String loginWarningReason =
                            i.getStringExtra(EXTRA_LOGIN_WARNING_REASON_FOR_ACCOUNT);
                    final Bundle args = AccountSettingsFragment.buildArguments(accountId,
                            loginWarningAccount, loginWarningReason);
                    startPreferencePanel(AccountSettingsFragment.class.getName(), args,
                            0, null, null, 0);
                }
            }
        }
        mShowDebugMenu = i.getBooleanExtra(EXTRA_ENABLE_DEBUG, false);

        getActionBar().setDisplayOptions(
                ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP);

        mFeedbackUri = Utils.getValidUri(getString(R.string.email_feedback_uri));
    }

    /**
     * Listen for secret sequence and, if heard, enable debug menu
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getKeyCode() == SECRET_KEY_CODES[mSecretKeyCodeIndex]) {
            mSecretKeyCodeIndex++;
            if (mSecretKeyCodeIndex == SECRET_KEY_CODES.length) {
                mSecretKeyCodeIndex = 0;
                enableDebugMenu();
            }
        } else {
            mSecretKeyCodeIndex = 0;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.settings_menu, menu);

        mFeedbackMenuItem = menu.findItem(R.id.feedback_menu_item);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        if (mFeedbackMenuItem != null) {
            // We only want to enable the feedback menu item, if there is a valid feedback uri
            mFeedbackMenuItem.setVisible(!Uri.EMPTY.equals(mFeedbackUri));
        }
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // The app icon on the action bar is pressed.  Just emulate a back press.
                // TODO: this should navigate to the main screen, even if a sub-setting is open.
                // But we shouldn't just finish(), as we want to show "discard changes?" dialog
                // when necessary.
                onBackPressed();
                break;
            case R.id.add_new_account:
                onAddNewAccount();
                break;
            case R.id.feedback_menu_item:
                Utils.sendFeedback(this, mFeedbackUri, false /* reportingProblem */);
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public boolean isValidFragment(String fragmentName) {
        // This activity is not exported, so we can allow any fragment
        return true;
    }


    private void launchMailboxSettings(Intent intent) {
        final Folder folder = intent.getParcelableExtra(EditSettingsExtras.EXTRA_FOLDER);

        // TODO: determine from the account if we should navigate to the mailbox settings.
        // See bug 6242668

        // Get the mailbox id from the folder
        final long mailboxId =
                Long.parseLong(folder.folderUri.fullUri.getPathSegments().get(1));

        MailboxSettings.start(this, mailboxId);
        finish();
    }

    private void enableDebugMenu() {
        mShowDebugMenu = true;
        invalidateHeaders();
    }

    private void onAddNewAccount() {
        final Intent setupIntent = AccountSetupFinal.actionNewAccountIntent(this);
        startActivity(setupIntent);
    }

    @Override
    public void onBuildExtraHeaders(List<Header> target) {
        super.onBuildExtraHeaders(target);
        // finally, if debug header is enabled, show it
        if (DEBUG_MENU_ALLOWED) {
            if (mShowDebugMenu) {
                // setup lightweight header for debugging
                final Header debugHeader = new Header();
                debugHeader.title = getText(R.string.debug_title);
                debugHeader.summary = null;
                debugHeader.iconRes = 0;
                debugHeader.fragment = DebugFragment.class.getCanonicalName();
                debugHeader.fragmentArguments = null;
                target.add(debugHeader);
            }
        }
    }

    /**
     * Called when the user selects an item in the header list.  Handles save-data cases as needed
     *
     * @param header The header that was selected.
     * @param position The header's position in the list.
     */
    @Override
    public void onHeaderClick(Header header, int position) {
        // Secret keys:  Click 10x to enable debug settings
        if (position == 0) {
            mNumGeneralHeaderClicked++;
            if (mNumGeneralHeaderClicked == 10) {
                enableDebugMenu();
            }
        } else {
            mNumGeneralHeaderClicked = 0;
        }

        // Process header click normally
        super.onHeaderClick(header, position);
    }

    @Override
    public void onAttachFragment(Fragment f) {
        super.onAttachFragment(f);
        // When we're changing fragments, enable/disable the add account button
        invalidateOptionsMenu();
    }
}
