package com.example.myapplication

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import java.util.concurrent.TimeUnit

/**
 * PIN entry dialog for two-factor authentication
 * Shows after successful biometric authentication
 */
class PinEntryDialog : DialogFragment() {
    
    companion object {
        private const val ARG_MODE = "mode"
        private const val ARG_TITLE = "title"
        private const val ARG_LOCKOUT_END_TIME = "lockout_end_time"
        
        const val MODE_SETUP = "setup"
        const val MODE_VERIFY = "verify"
        
        fun newInstance(mode: String, title: String, lockoutEndTime: Long = 0L): PinEntryDialog {
            val fragment = PinEntryDialog()
            val args = Bundle()
            args.putString(ARG_MODE, mode)
            args.putString(ARG_TITLE, title)
            args.putLong(ARG_LOCKOUT_END_TIME, lockoutEndTime)
            fragment.arguments = args
            return fragment
        }
    }
    
    interface PinEntryListener {
        fun onPinEntered(pin: String, isSetup: Boolean)
        fun onPinCancelled()
    }
    
    private var listener: PinEntryListener? = null
    private lateinit var pinDots: Array<View>
    private lateinit var numberButtons: Array<Button>
    private lateinit var deleteButton: Button
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var errorText: TextView
    private lateinit var authenticationManager: AuthenticationManager
    
    private var currentPin = ""
    private var confirmPin = ""
    private var isSetupMode = false
    private var isConfirmStep = false
    private var isLocked = false
    
    fun setPinEntryListener(listener: PinEntryListener) {
        this.listener = listener
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val mode = arguments?.getString(ARG_MODE) ?: MODE_VERIFY
        val title = arguments?.getString(ARG_TITLE) ?: "Enter PIN"
        val lockoutEndTime = arguments?.getLong(ARG_LOCKOUT_END_TIME) ?: 0L
        
        isSetupMode = mode == MODE_SETUP
        isLocked = lockoutEndTime > System.currentTimeMillis()
        
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_pin_entry, null)
        setupViews(view)
        updateUI(title, lockoutEndTime)
        
        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .setCancelable(true)
            .create()
            
        dialog.setOnCancelListener {
            listener?.onPinCancelled()
        }
        
        return dialog
    }
    
    private fun setupViews(view: View) {
        titleText = view.findViewById(R.id.pinTitleText)
        subtitleText = view.findViewById(R.id.pinSubtitleText)
        errorText = view.findViewById(R.id.pinErrorText)
        
        // Setup PIN dots
        pinDots = arrayOf(
            view.findViewById(R.id.pinDot1),
            view.findViewById(R.id.pinDot2),
            view.findViewById(R.id.pinDot3),
            view.findViewById(R.id.pinDot4)
        )
        
        // Setup number buttons
        numberButtons = arrayOf(
            view.findViewById(R.id.button0),
            view.findViewById(R.id.button1),
            view.findViewById(R.id.button2),
            view.findViewById(R.id.button3),
            view.findViewById(R.id.button4),
            view.findViewById(R.id.button5),
            view.findViewById(R.id.button6),
            view.findViewById(R.id.button7),
            view.findViewById(R.id.button8),
            view.findViewById(R.id.button9)
        )
        
        deleteButton = view.findViewById(R.id.buttonDelete)
        
        // Setup click listeners
        numberButtons.forEachIndexed { index, button ->
            button.setOnClickListener { onNumberPressed(index.toString()) }
        }
        
        deleteButton.setOnClickListener { onDeletePressed() }
        
        view.findViewById<Button>(R.id.buttonCancel).setOnClickListener {
            listener?.onPinCancelled()
            dismiss()
        }
        
        // Disable input if locked
        if (isLocked) {
            numberButtons.forEach { it.isEnabled = false }
            deleteButton.isEnabled = false
        }
    }
    
    private fun updateUI(title: String, lockoutEndTime: Long = 0L) {
        titleText.text = title
        
        when {
            isLocked -> {
                val remainingSeconds = (lockoutEndTime - System.currentTimeMillis()) / 1000
                val remainingMinutes = remainingSeconds / 60
                subtitleText.text = getString(R.string.pin_locked_message, remainingMinutes)
                errorText.visibility = View.VISIBLE
                errorText.text = getString(R.string.too_many_failed_attempts)
            }
            isSetupMode && !isConfirmStep -> {
                subtitleText.text = getString(R.string.create_pin_message)
            }
            isSetupMode && isConfirmStep -> {
                subtitleText.text = getString(R.string.confirm_pin_message)
            }
            else -> {
                subtitleText.text = getString(R.string.enter_pin_message)
            }
        }
        
        updatePinDots()
        if (!isLocked) {
           errorText.visibility = View.GONE
        }
    }
    
    private fun onNumberPressed(number: String) {
        if (currentPin.length < 4) {
            currentPin += number
            updatePinDots()
            
            if (currentPin.length == 4) {
                // PIN complete, handle based on mode
                Handler(Looper.getMainLooper()).postDelayed({
                    handlePinComplete()
                }, 200) // Small delay for visual feedback
            }
        }
    }
    
    private fun onDeletePressed() {
        if (currentPin.isNotEmpty()) {
            currentPin = currentPin.dropLast(1)
            updatePinDots()
        }
    }
    
    private fun updatePinDots() {
        pinDots.forEachIndexed { index, dot ->
            dot.isSelected = index < currentPin.length
        }
    }
    
    private fun handlePinComplete() {
        when {
            isSetupMode && !isConfirmStep -> {
                // First PIN entry in setup, ask for confirmation
                confirmPin = currentPin
                currentPin = ""
                isConfirmStep = true
                updateUI("Confirm PIN")
            }
            isSetupMode && isConfirmStep -> {
                // Confirming PIN in setup
                if (currentPin == confirmPin) {
                    listener?.onPinEntered(currentPin, true)
                    dismiss()
                } else {
                    showError("PINs don't match. Try again.")
                    resetToFirstStep()
                }
            }
            else -> {
                // Verifying existing PIN
                listener?.onPinEntered(currentPin, false)
                // Do not dismiss immediately, wait for verification result from activity
            }
        }
    }
    
    private fun resetToFirstStep() {
        currentPin = ""
        confirmPin = ""
        isConfirmStep = false
        updateUI("Create PIN")
    }
    
    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
        
        // Clear current input
        currentPin = ""
        updatePinDots()
        
        // Hide error after delay
        Handler(Looper.getMainLooper()).postDelayed({
            if (isAdded) {
                errorText.visibility = View.GONE
            }
        }, 3000)
    }
    
    /**
     * Show error from outside (for failed verification)
     */
    fun showVerificationError(message: String) {
        showError(message)
    }

    fun showWeakPinError() {
        showError("This PIN is too common. Please choose a different one.")
        resetToFirstStep()
    }

    private fun checkLockout(): Boolean {
        if (authenticationManager.isPinLocked()) {
            val remainingTime = authenticationManager.getRemainingPinLockoutTime()
            val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingTime)
            showError("PIN entry is locked for $minutes minutes.")
            // Disable buttons
            numberButtons.forEach { it.isEnabled = false }
            deleteButton.isEnabled = false
            return true
        }
        return false
    }
} 