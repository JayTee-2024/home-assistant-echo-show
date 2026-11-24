/*
 * The sign-in screen's logic, without views: which form shows, what it says, and what each button does.
 * A port of the LG app's SignInScreen.tsx and back.ts. The forms:
 *
 *   LOOKING      trying an address; nothing to type
 *   ADDRESS      type the Home Assistant address
 *   CREDENTIALS  username and password
 *   CODE         a two-step code
 *   TOKEN        the "Advanced" route: a long-lived access token
 *
 * The password and code are passed straight to Home Assistant and never kept here.
 */
package net.badacid.homeassist.echoshow.screens.signin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.badacid.homeassist.echoshow.core.AddressResult
import net.badacid.homeassist.echoshow.core.DEFAULT_ADDRESS
import net.badacid.homeassist.echoshow.core.HttpSend
import net.badacid.homeassist.echoshow.core.SavedSignIn
import net.badacid.homeassist.echoshow.core.SignInStep
import net.badacid.homeassist.echoshow.core.beginSignIn
import net.badacid.homeassist.echoshow.core.normaliseAddress
import net.badacid.homeassist.echoshow.core.signInWithToken
import net.badacid.homeassist.echoshow.core.submitCode
import net.badacid.homeassist.echoshow.core.submitCredentials

enum class SignInForm { LOOKING, ADDRESS, CREDENTIALS, CODE, TOKEN }

data class SignInUiState(
    val form: SignInForm = SignInForm.LOOKING,
    /** The address being used, for example http://192.168.1.100:8123. */
    val address: String = "",
    val message: String? = null,
    /** True while waiting for Home Assistant: the main button ignores presses. */
    val busy: Boolean = false,
    /** What the address field starts with when the address form shows. */
    val typedAddress: String = "",
    /** What the token field starts with: the token Home Assistant refused, to correct. */
    val typedToken: String? = null,
    /** Counts Home Assistant's answers; the view clears the password and code on each one. */
    val answers: Int = 0,
) {
    /** The address as shown: without http://, but an https address in full. */
    val shownAddress: String get() = if (address.startsWith("http://")) address.removePrefix("http://") else address
}

class SignInFlow(
    private val send: HttpSend,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val startAddress: String?,
    private val notice: String?,
    private val refusedToken: String?,
    private val onSignedIn: (SavedSignIn) -> Unit,
) {
    private val flow = MutableStateFlow(SignInUiState(address = startAddress ?: "http://$DEFAULT_ADDRESS", typedToken = refusedToken))
    val state: StateFlow<SignInUiState> = flow.asStateFlow()

    private var flowId = ""

    /** Counts requests; an answer is used only if no newer request or form change has happened since. */
    private var request = 0

    /** On first showing: try the starting address at once, or show the refused token to correct. */
    fun start() {
        if (refusedToken != null) {
            flow.value = flow.value.copy(form = SignInForm.TOKEN, message = notice)
        } else {
            tryAddress(flow.value.address)
        }
    }

    /** "Change": edit the address. */
    fun change() {
        val address = flow.value.address
        goTo(SignInForm.ADDRESS)
        flow.value = flow.value.copy(typedAddress = addressToEdit(address))
    }

    /** "Connect" on the address form. */
    fun connect(typed: String) {
        when (val result = normaliseAddress(typed)) {
            AddressResult.Empty -> say("Enter an address.")
            AddressResult.Invalid -> say("That is not a valid address.")
            is AddressResult.Ok -> tryAddress(result.url)
        }
    }

    /**
     * "Advanced: use an access token" from the address form, for when the usual sign-in cannot start.
     * The token is used with the address typed in the field.
     */
    fun useTokenFor(typed: String) {
        val result = normaliseAddress(typed)
        if (result !is AddressResult.Ok) return say("Enter the address of your Home Assistant first.")
        flow.value = flow.value.copy(address = result.url)
        goTo(SignInForm.TOKEN)
    }

    /** "Advanced: use an access token" from the credentials form. */
    fun goToToken() = goTo(SignInForm.TOKEN)

    /** "Back to username and password" on the token form. */
    fun backToCredentials() = tryAddress(flow.value.address)

    fun signIn(username: String, password: String) {
        if (username.isEmpty() || password.isEmpty()) return say("Enter your username and password.")
        val address = flow.value.address
        flow.value = flow.value.copy(busy = true, message = null)
        ask(address) { submitCredentials(send, address, flowId, username, password, clock()) }
    }

    fun verify(code: String) {
        val address = flow.value.address
        flow.value = flow.value.copy(busy = true, message = null)
        ask(address) { submitCode(send, address, flowId, code, clock()) }
    }

    fun useToken(token: String) {
        if (token.isBlank()) return say("Enter an access token.")
        onSignedIn(signInWithToken(flow.value.address, token))
    }

    /** The system Back: one form back. False on the first forms, where there is nowhere to go back to. */
    fun back(): Boolean = when (flow.value.form) {
        SignInForm.CREDENTIALS -> {
            change()
            true
        }
        SignInForm.CODE, SignInForm.TOKEN -> {
            tryAddress(flow.value.address)
            true
        }
        SignInForm.LOOKING, SignInForm.ADDRESS -> false
    }

    private fun tryAddress(candidate: String) {
        flow.value = flow.value.copy(address = candidate, form = SignInForm.LOOKING, message = notice, busy = false)
        ask(candidate) { beginSignIn(send, candidate) }
    }

    private fun ask(triedAddress: String, work: suspend () -> SignInStep) {
        request += 1
        val thisRequest = request
        scope.launch {
            val step = work()
            if (request == thisRequest) show(step, triedAddress)
        }
    }

    /** Shows the form the step calls for, or hands the finished sign-in on. */
    private fun show(step: SignInStep, triedAddress: String) {
        val answered = flow.value.copy(busy = false, answers = flow.value.answers + 1)
        flow.value = when (step) {
            is SignInStep.SignedIn -> {
                flow.value = answered
                onSignedIn(step.signIn)
                return
            }
            // Home Assistant's own message for this step, or else the notice the screen started with.
            is SignInStep.Credentials -> {
                flowId = step.flowId
                answered.copy(form = SignInForm.CREDENTIALS, message = step.message ?: notice)
            }
            is SignInStep.Code -> {
                flowId = step.flowId
                answered.copy(form = SignInForm.CODE, message = step.message ?: notice)
            }
            // Could not reach it, not Home Assistant, or refused: ask for the address.
            is SignInStep.CannotReach -> answered.toAddress(step.message, triedAddress)
            is SignInStep.NotHomeAssistant -> answered.toAddress(step.message, triedAddress)
            is SignInStep.Failed -> answered.toAddress(step.message, triedAddress)
        }
    }

    private fun SignInUiState.toAddress(message: String, tried: String) =
        copy(form = SignInForm.ADDRESS, message = message, typedAddress = addressToEdit(tried))

    /** Moves to another form, dropping any answer still on its way. */
    private fun goTo(form: SignInForm) {
        request += 1
        flow.value = flow.value.copy(form = form, busy = false, message = null)
    }

    private fun say(message: String) {
        flow.value = flow.value.copy(message = message)
    }

    private fun addressToEdit(address: String): String =
        if (address == "http://$DEFAULT_ADDRESS") "" else address.removePrefix("http://")
}
