package com.appboy.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import com.appboy.Appboy;
import com.appboy.support.AppboyLogger;
import com.appboy.support.StringUtils;
import com.appboy.support.ValidationUtils;

public class AppboyFeedbackFragment extends Fragment {
  private static final String TAG = AppboyLogger.getAppboyLogTag(AppboyFeedbackFragment.class);

  /**
   * Listener called in response to feedback lifecycle events.
   */
  public interface IFeedbackFinishedListener {

    /**
     * Called when the user finishes the feedback fragment by submitting feedback or cancelling. Note that {@link FeedbackResult#SUBMITTED} will always be fired if the user sends
     * feedback, even if the subsequent request fails.
     *
     * Note that the value of {@link FeedbackResult} returns immediately and does not take into account the success of the feedback request.
     *
     * @param feedbackResult the result of the user pressing submit or cancelling the feedback.
     */
    void onFeedbackFinished(FeedbackResult feedbackResult);

    /**
     * Called just before a user-submitted feedback message is sent to Appboy.
     * <p/>
     * Allows modification or augmentation of the message before it is sent to Appboy.
     *
     * @param message the feedback message as written by the user
     * @return the feedback message that will be sent to Appboy
     */
    String beforeFeedbackSubmitted(String message);
  }

  public enum FeedbackResult {
    SUBMITTED, CANCELLED
  }

  private Button mCancelButton;
  private Button mSendButton;
  private CheckBox mIsBugCheckBox;
  private EditText mMessageEditText;
  private EditText mEmailEditText;
  private TextWatcher mSendButtonWatcher;
  private View.OnClickListener mCancelListener;
  private View.OnClickListener mSendListener;
  private IFeedbackFinishedListener mFeedbackFinishedListener;
  private int mOriginalSoftInputMode;
  private boolean mErrorMessageShown;

  public AppboyFeedbackFragment() {
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    mSendButtonWatcher = new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence sequence, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence sequence, int start, int before, int count) {
      }

      @Override
      public void afterTextChanged(Editable sequence) {
        if (mErrorMessageShown) {
          // Only show error messages after the user has clicked the send button at least once.
          ensureSendButton();
        }
      }
    };
    mCancelListener = new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        hideSoftKeyboard();
        if (mFeedbackFinishedListener != null) {
          mFeedbackFinishedListener.onFeedbackFinished(FeedbackResult.CANCELLED);
        }
        clearData();
      }
    };
    mSendListener = new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (ensureSendButton()) {
          hideSoftKeyboard();
          boolean isBug = mIsBugCheckBox.isChecked();
          String message = mMessageEditText.getText().toString();
          String email = mEmailEditText.getText().toString();
          if (mFeedbackFinishedListener != null) {
            message = mFeedbackFinishedListener.beforeFeedbackSubmitted(message);
          }
          Appboy.getInstance(getActivity()).submitFeedback(email, message, isBug);
          if (mFeedbackFinishedListener != null) {
            mFeedbackFinishedListener.onFeedbackFinished(FeedbackResult.SUBMITTED);
          }
          clearData();
        } else {
          mErrorMessageShown = true;
        }
      }
    };
    setRetainInstance(true);
  }

  @Override
  public View onCreateView(LayoutInflater layoutInflater, ViewGroup container, Bundle savedInstanceState) {
    View view = layoutInflater.inflate(R.layout.com_appboy_feedback, container, false);
    mCancelButton = (Button) view.findViewById(R.id.com_appboy_feedback_cancel);
    mSendButton = (Button) view.findViewById(R.id.com_appboy_feedback_send);
    mIsBugCheckBox = (CheckBox) view.findViewById(R.id.com_appboy_feedback_is_bug);
    mMessageEditText = (EditText) view.findViewById(R.id.com_appboy_feedback_message);
    mEmailEditText = (EditText) view.findViewById(R.id.com_appboy_feedback_email);

    mMessageEditText.addTextChangedListener(mSendButtonWatcher);
    mEmailEditText.addTextChangedListener(mSendButtonWatcher);
    mCancelButton.setOnClickListener(mCancelListener);
    mSendButton.setOnClickListener(mSendListener);
    return view;
  }

  @Override
  public void onResume() {
    super.onResume();
    Appboy.getInstance(getActivity()).logFeedbackDisplayed();

    Activity activity = getActivity();
    Window window = activity.getWindow();

    // Overriding the soft input mode of the Window so that the Send and Cancel buttons appear above
    // the soft keyboard when either EditText field gains focus. We cache the mode in order to set it
    // back to the original value when the Fragment is paused.
    mOriginalSoftInputMode = window.getAttributes().softInputMode;
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

    Appboy.getInstance(activity).logFeedbackDisplayed();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    mMessageEditText.removeTextChangedListener(mSendButtonWatcher);
    mEmailEditText.removeTextChangedListener(mSendButtonWatcher);
  }

  public void setFeedbackFinishedListener(IFeedbackFinishedListener feedbackFinishedListener) {
    mFeedbackFinishedListener = feedbackFinishedListener;
  }

  public EditText getMessageEditText() {
    return mMessageEditText;
  }

  public EditText getEmailEditText() {
    return mEmailEditText;
  }

  private boolean validatedMessage() {
    boolean validMessage = mMessageEditText.getText() != null && !StringUtils.isNullOrBlank(mMessageEditText.getText().toString());
    if (validMessage) {
      mMessageEditText.setError(null);
    } else {
      // Display error message in the message box
      displayMessageTextError(R.string.com_appboy_feedback_form_invalid_message);
    }
    return validMessage;
  }

  private boolean validatedEmail() {
    boolean validEmail = mEmailEditText.getText() != null
        && !StringUtils.isNullOrBlank(mEmailEditText.getText().toString())
        && ValidationUtils.isValidEmailAddress(mEmailEditText.getText().toString());
    boolean blankEmail = mEmailEditText.getText() != null && StringUtils.isNullOrBlank(mEmailEditText.getText().toString());
    if (validEmail) {
      mEmailEditText.setError(null);
    } else if (blankEmail) {
      // Display blank email error message in the email box
      displayEmailTextError(R.string.com_appboy_feedback_form_empty_email);
    } else {
      // Display general invalid email error message in the email box
      displayEmailTextError(R.string.com_appboy_feedback_form_invalid_email);
    }
    return validEmail;
  }

  private void displayEmailTextError(int resourceId) {
    if (getActivity() != null) {
      mEmailEditText.setError(getResources().getString(resourceId));
    } else {
      AppboyLogger.w(TAG, "Activity is null. Cannot set feedback form email error message");
    }
  }

  private void displayMessageTextError(int resourceId) {
    if (getActivity() != null) {
      mMessageEditText.setError(getResources().getString(resourceId));
    } else {
      AppboyLogger.w(TAG, "Activity is null. Cannot set feedback form message error.");
    }
  }

  private boolean ensureSendButton() {
    // Both validators should run, so don't short-circuit this AND statement.
    return (validatedMessage() & validatedEmail());
  }

  private void clearData() {
    mEmailEditText.setText("");
    mMessageEditText.setText("");
    mIsBugCheckBox.setChecked(false);
    mErrorMessageShown = false;
    mEmailEditText.setError(null);
    mMessageEditText.setError(null);
  }

  private void hideSoftKeyboard() {
    Activity activity = getActivity();
    activity.getWindow().setSoftInputMode(mOriginalSoftInputMode);

    // Hide keyboard when paused.
    View currentFocusView = activity.getCurrentFocus();

    if (currentFocusView != null) {
      InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
      inputMethodManager.hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(),
          InputMethodManager.RESULT_UNCHANGED_SHOWN);
    }
  }
}
