package com.taskflow.app.ui.sheets

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.taskflow.app.R
import com.taskflow.app.data.Categories
import com.taskflow.app.data.Habit
import com.taskflow.app.data.HabitFrequency
import com.taskflow.app.data.Store
import com.taskflow.app.databinding.SheetHabitEditorBinding
import com.taskflow.app.ui.sheets.VoiceDictationSheet
import java.util.UUID

class HabitEditorSheet : BottomSheetDialogFragment() {

    private var _binding: SheetHabitEditorBinding? = null
    private val binding get() = _binding!!
    private val palette = listOf("#06B6D4", "#8B5CF6", "#10B981", "#F59E0B", "#F43F5E", "#4F46E5")
    private var selectedColor = palette.first()
    private var category = "Saúde & Bem-estar"
    private var frequency = HabitFrequency.DAILY

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetHabitEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        parentFragmentManager.setFragmentResultListener("voice_habit_name", this) { _, bundle ->
            val text = bundle.getString("text").orEmpty().trim()
            if (text.isNotBlank()) binding.nameInput.setText(text)
        }
        binding.nameLayout.setEndIconOnClickListener {
            VoiceDictationSheet.newInstance("habits", VoiceDictationSheet.MODE_FIELD, "voice_habit_name")
                .show(parentFragmentManager, "voice_habit_name")
        }
        if (Store.settings.voicePreferred) {
            binding.nameInput.showSoftInputOnFocus = false
            binding.nameInput.setOnClickListener {
                VoiceDictationSheet.newInstance("habits", VoiceDictationSheet.MODE_FIELD, "voice_habit_name")
                    .show(parentFragmentManager, "voice_habit_name")
            }
        }

        binding.categoryValue.text = category
        binding.frequencyValue.text = frequency.label
        binding.categoryField.setOnClickListener {
            val names = Categories.all.map { it.name }.toTypedArray()
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Categoria")
                .setSingleChoiceItems(names, Categories.all.indexOfFirst { it.name == category }) { dialog, which ->
                    category = names[which]
                    binding.categoryValue.text = category
                    dialog.dismiss()
                }
                .show()
        }
        binding.frequencyField.setOnClickListener {
            val labels = HabitFrequency.entries.map { it.label }.toTypedArray()
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Frequência")
                .setSingleChoiceItems(labels, HabitFrequency.entries.indexOf(frequency)) { dialog, which ->
                    frequency = HabitFrequency.entries[which]
                    binding.frequencyValue.text = frequency.label
                    dialog.dismiss()
                }
                .show()
        }

        renderColors()

        binding.btnCreate.setOnClickListener {
            val name = binding.nameInput.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) {
                binding.nameInput.error = "Dê um nome ao hábito"
                return@setOnClickListener
            }
            Store.addHabit(
                Habit(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    category = category,
                    colorHex = selectedColor,
                    frequency = frequency
                )
            )
            dismiss()
        }
    }

    private fun renderColors() {
        binding.colorRow.removeAllViews()
        palette.forEach { hex ->
            val view = TextView(requireContext()).apply {
                text = if (hex == selectedColor) "✓" else ""
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                    marginEnd = dp(10)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(hex))
                    if (hex == selectedColor) {
                        setStroke(dp(3), Color.WHITE)
                    }
                }
                setOnClickListener {
                    selectedColor = hex
                    renderColors()
                }
            }
            binding.colorRow.addView(view)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
