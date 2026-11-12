package kr.ansimclean.customer;

import android.os.CancellationSignal;
import androidx.core.content.ContextCompat;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.kakao.sdk.common.KakaoSdk;
import com.kakao.sdk.common.model.AuthError;
import com.kakao.sdk.common.model.ApiError;
import com.kakao.sdk.common.model.ClientError;
import com.kakao.sdk.common.model.ClientErrorCause;
import com.kakao.sdk.user.UserApiClient;
import kotlin.Unit;

/** Native tokens are returned only over Capacitor's bridge and are never logged. */
@CapacitorPlugin(name = "SocialAuth")
public class SocialAuthPlugin extends Plugin {
    private boolean busy;
    private CancellationSignal cancellation;
    @PluginMethod
    public void signIn(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (busy) { call.reject("Login already running", "BUSY"); return; }
            String provider = call.getString("provider", "");
            String nonce = call.getString("nonce", "");
            if (nonce.isEmpty()) { call.reject("Nonce required", "CONFIGURATION"); return; }
            busy = true;
            if ("google".equals(provider)) google(call, nonce);
            else if ("kakao".equals(provider)) kakao(call, nonce);
            else fail(call, "UNSUPPORTED");
        });
    }
    private void google(PluginCall call, String nonce) {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isEmpty()) { fail(call, "CONFIGURATION"); return; }
        GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(nonce).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(option).build();
        cancellation = new CancellationSignal();
        CredentialManager.create(getContext()).getCredentialAsync(getActivity(), request, cancellation,
            ContextCompat.getMainExecutor(getContext()), new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                @Override public void onResult(GetCredentialResponse result) {
                    try {
                        if (!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(result.getCredential().getType())) {
                            fail(call, "AUTH_FAILED"); return;
                        }
                        success(call, GoogleIdTokenCredential.createFrom(result.getCredential().getData()).getIdToken());
                    } catch (Exception error) { fail(call, "AUTH_FAILED"); }
                }
                @Override public void onError(GetCredentialException error) {
                    fail(call, error instanceof GetCredentialCancellationException ? "CANCELLED" : "AUTH_FAILED");
                }
            });
    }
    private void kakao(PluginCall call, String nonce) {
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isEmpty()) { fail(call, "CONFIGURATION"); return; }
        KakaoSdk.init(getContext(), BuildConfig.KAKAO_NATIVE_APP_KEY);
        UserApiClient api = UserApiClient.getInstance();
        if (!api.isKakaoTalkLoginAvailable(getContext())) { kakaoAccount(call, nonce); return; }
        // The Kakao SDK browser flow uses configured consent items, unlike Supabase OAuth defaults.
        api.loginWithKakaoTalk(getActivity(), 10012, nonce, null, null, (token, error) -> {
            if (error != null) {
                boolean cancelled = error instanceof ClientError && ((ClientError) error).getReason() == ClientErrorCause.Cancelled;
                if (cancelled) fail(call, "CANCELLED");
                else kakaoAccount(call, nonce);
            } else if (token == null || token.getIdToken() == null) fail(call, "OIDC_REQUIRED");
            else success(call, token.getIdToken());
            return Unit.INSTANCE;
        });
    }
    private void kakaoAccount(PluginCall call, String nonce) {
        // No email/profile scopes are added. OIDC is enabled independently in Kakao Console.
        UserApiClient.getInstance().loginWithKakaoAccount(getActivity(), null, null, nonce, null, null, (token, error) -> {
            if (error != null) {
                boolean cancelled = error instanceof ClientError && ((ClientError) error).getReason() == ClientErrorCause.Cancelled;
                if (cancelled) fail(call, "CANCELLED");
                else failKakao(call, error);
            } else if (token == null || token.getIdToken() == null) fail(call, "OIDC_REQUIRED");
            else success(call, token.getIdToken());
            return Unit.INSTANCE;
        });
    }
    private void failKakao(PluginCall call, Throwable error) {
        // Only SDK enum values/status are reported. Never log raw exception responses.
        String code = "KAKAO_AUTH_FAILED";
        if (error instanceof AuthError) {
            AuthError auth = (AuthError) error;
            code = "KAKAO_AUTH_" + auth.getReason().name() + "_" + auth.getStatusCode();
        } else if (error instanceof ApiError) {
            ApiError api = (ApiError) error;
            code = "KAKAO_API_" + api.getReason().name() + "_" + api.getStatusCode();
        } else if (error instanceof ClientError) {
            code = "KAKAO_CLIENT_" + ((ClientError) error).getReason().name();
        }
        android.util.Log.w("SocialAuthStatus", code);
        fail(call, code);
    }
    private void success(PluginCall call, String token) {
        busy = false;
        cancellation = null;
        JSObject result = new JSObject(); result.put("idToken", token); call.resolve(result);
    }
    private void fail(PluginCall call, String code) {
        busy = false; cancellation = null; call.reject("Social authentication did not complete", code);
    }
    @Override protected void handleOnDestroy() {
        if (cancellation != null) cancellation.cancel();
        busy = false;
    }
}
