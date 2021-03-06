/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
/**
 * 
 */
package org.roda.wui.client.main;

import java.util.Arrays;
import java.util.List;

import org.roda.core.data.exceptions.AuthenticationDeniedException;
import org.roda.core.data.exceptions.EmailUnverifiedException;
import org.roda.core.data.exceptions.InactiveUserException;
import org.roda.core.data.v2.notifications.Notification;
import org.roda.core.data.v2.user.User;
import org.roda.wui.client.common.UserLogin;
import org.roda.wui.client.common.dialogs.Dialogs;
import org.roda.wui.client.common.utils.StringUtils;
import org.roda.wui.client.management.RecoverLogin;
import org.roda.wui.client.management.Register;
import org.roda.wui.client.management.UserManagementService;
import org.roda.wui.client.welcome.Welcome;
import org.roda.wui.common.client.ClientLogger;
import org.roda.wui.common.client.HistoryResolver;
import org.roda.wui.common.client.tools.HistoryUtils;
import org.roda.wui.common.client.widgets.Toast;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.AttachEvent;
import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PasswordTextBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;

import config.i18n.client.ClientMessages;

/**
 * @author Luis Faria
 * 
 */
public class Login extends Composite {

  public static final HistoryResolver RESOLVER = new HistoryResolver() {

    @Override
    public void resolve(List<String> historyTokens, AsyncCallback<Widget> callback) {
      getInstance().resolve(historyTokens, callback);
    }

    @Override
    public void isCurrentUserPermitted(AsyncCallback<Boolean> callback) {
      callback.onSuccess(true);
    }

    @Override
    public String getHistoryToken() {
      return "login";
    }

    @Override
    public List<String> getHistoryPath() {
      return Arrays.asList(getHistoryToken());
    }
  };

  public static final String getViewItemHistoryToken(String id) {
    return RESOLVER.getHistoryPath() + "." + id;
  }

  interface MyUiBinder extends UiBinder<Widget, Login> {
  }

  private static MyUiBinder uiBinder = GWT.create(MyUiBinder.class);
  private static ClientMessages messages = (ClientMessages) GWT.create(ClientMessages.class);

  private static Login instance = null;

  /**
   * Get the singleton instance
   * 
   * @return the instance
   */
  public static Login getInstance() {
    if (instance == null) {
      instance = new Login();
    }
    return instance;
  }

  @SuppressWarnings("unused")
  private ClientLogger logger = new ClientLogger(getClass().getName());

  @UiField
  TextBox username;

  @UiField
  PasswordTextBox password;

  @UiField
  Label error;

  @UiField
  Button resendEmail;

  private String service = null;

  private Login() {
    initWidget(uiBinder.createAndBindUi(this));

    addAttachHandler(new AttachEvent.Handler() {
      @Override
      public void onAttachOrDetach(AttachEvent event) {
        if (event.isAttached()) {
          username.setFocus(true);
        }
      }
    });

  }

  public void resolve(List<String> historyTokens, AsyncCallback<Widget> callback) {
    username.setText("");
    password.setText("");
    error.setText("");
    resendEmail.setVisible(false);
    service = StringUtils.join(historyTokens, HistoryUtils.HISTORY_SEP);
    callback.onSuccess(this);
  }

  @UiHandler("login")
  void handleLogin(ClickEvent e) {
    doLogin();
  }

  @UiHandler("register")
  void handleRegister(ClickEvent e) {
    HistoryUtils.newHistory(Register.RESOLVER);
  }

  @UiHandler("recover")
  void handleRecover(ClickEvent e) {
    HistoryUtils.newHistory(RecoverLogin.RESOLVER);
  }

  @UiHandler("username")
  void handleUsernameKeyPress(KeyPressEvent event) {
    tryToLoginWhenEnterIsPressed(event);
  }

  @UiHandler("password")
  void handlePasswordKeyPress(KeyPressEvent event) {
    tryToLoginWhenEnterIsPressed(event);
  }

  @UiHandler("resendEmail")
  void handleResendEmail(final ClickEvent e) {

    UserManagementService.Util.getInstance().sendEmailVerification(username.getText(), true,
      LocaleInfo.getCurrentLocale().getLocaleName(), new AsyncCallback<Notification>() {

        @Override
        public void onSuccess(final Notification result) {
          if (result.getState() == Notification.NOTIFICATION_STATE.COMPLETED) {
            Dialogs.showInformationDialog(messages.loginResendEmailSuccessDialogTitle(),
              messages.loginResendEmailSuccessDialogMessage(), messages.loginResendEmailSuccessDialogButton(),
              new AsyncCallback<Void>() {

                @Override
                public void onSuccess(final Void result) {
                  HistoryUtils.newHistory(Login.RESOLVER);
                }

                @Override
                public void onFailure(final Throwable caught) {
                  HistoryUtils.newHistory(Login.RESOLVER);
                }
              });
          } else {
            Dialogs.showInformationDialog(messages.loginResendEmailFailureDialogTitle(),
              messages.loginResendEmailFailureDialogMessage(), messages.loginResendEmailFailureDialogButton(),
              new AsyncCallback<Void>() {

                @Override
                public void onSuccess(final Void result) {
                  HistoryUtils.newHistory(Login.RESOLVER);
                }

                @Override
                public void onFailure(final Throwable caught) {
                  HistoryUtils.newHistory(Login.RESOLVER);
                }
              });
          }
        }

        @Override
        public void onFailure(final Throwable caught) {
          Toast.showError(messages.loginResendEmailVerificationFailure());
        }
      });
  }

  private void tryToLoginWhenEnterIsPressed(KeyPressEvent event) {
    if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
      doLogin();
    }
  }

  private void doLogin() {
    String usernameText = username.getText();
    String passwordText = password.getText();
    error.setText("");

    if (usernameText.trim().length() == 0 || passwordText.trim().length() == 0) {
      error.setText(messages.fillUsernameAndPasswordMessage());
    } else {

      UserLogin.getInstance().login(usernameText, passwordText, new AsyncCallback<User>() {

        @Override
        public void onFailure(Throwable caught) {
          if (caught instanceof EmailUnverifiedException) {
            error.setText(messages.emailUnverifiedMessage());
            resendEmail.setVisible(true);
          } else if (caught instanceof InactiveUserException) {
            error.setText(messages.inactiveUserMessage());
          } else if (caught instanceof AuthenticationDeniedException) {
            error.setText(messages.wrongUsernameAndPasswordMessage());
          } else {
            error.setText(messages.systemCurrentlyUnavailableMessage());
          }
        }

        @Override
        public void onSuccess(User user) {
          if (service != null && service.length() > 0) {
            History.newItem(service);
          } else {
            HistoryUtils.newHistory(Welcome.RESOLVER);
          }
        }
      });
    }
  }
}
