package dev.defvs.chatterz.settings

import android.content.Context
import android.content.res.TypedArray
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.defvs.chatterz.R
import io.multimoon.colorful.ThemeColor

class ColorPickerPreference(context: Context?, attrs: AttributeSet?) :
	DialogPreference(context, attrs) {
	
	var selectedColorIndex: Int = 0
		set(value) {
			persistInt(value)
			field = value
		}
	
	val selectedColor: ThemeColor
		get() = colors[selectedColorIndex]
	
	init {
		dialogLayoutResource = R.layout.color_picker_preference
		setPositiveButtonText(android.R.string.ok)
		setNegativeButtonText(android.R.string.cancel)
	}
	
	override fun onGetDefaultValue(a: TypedArray?, index: Int) = a?.getInt(index, 0) ?: 0
	override fun onSetInitialValue(defaultValue: Any?) {
		selectedColorIndex = getPersistedInt(defaultValue as Int)
	}
	
	companion object {
		val colors = ThemeColor.values()
	}
}

class ColorPickerDialog : PreferenceDialogFragmentCompat() {
	
	private lateinit var recyclerView: RecyclerView
	private lateinit var colorListAdapter: ColorListAdapter
	
	companion object {
		fun newInstance(key: String) = ColorPickerDialog().apply {
			arguments = Bundle(1).apply { putString(ARG_KEY, key) }
		}
	}
	
	override fun onBindDialogView(view: View) {
		super.onBindDialogView(view)
		
		recyclerView = view.findViewById(R.id.color_picker_list)
		recyclerView.apply {
			setHasFixedSize(true)
			layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
			colorListAdapter = ColorListAdapter(ColorPickerPreference.colors)
			adapter = colorListAdapter
		}
		val selectedColor = (preference as? ColorPickerPreference)?.selectedColorIndex
		if (selectedColor != null) {
			colorListAdapter.selectedIndex = selectedColor
		}
	}
	
	override fun onDialogClosed(positiveResult: Boolean) {
		if (positiveResult) {
			(preference as? ColorPickerPreference)?.let {
				if (it.callChangeListener(colorListAdapter.selectedIndex))
					it.selectedColorIndex = colorListAdapter.selectedIndex
			}
		}
	}
	
	class ColorListAdapter(private val colors: Array<ThemeColor>) :
		RecyclerView.Adapter<ColorListAdapter.ViewHolder>() {
		class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
		
		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
			LayoutInflater.from(parent.context).inflate(R.layout.color_list_item, parent, false)
		)
		
		override fun getItemCount() = colors.size
		
		var selectedIndex = 0
		
		override fun onBindViewHolder(holder: ViewHolder, position: Int) {
			with(holder.itemView) {
				setOnClickListener {
					selectedIndex = position
					notifyDataSetChanged()
				}
				val drawableView = findViewById<ImageView>(R.id.color_drawable)
				val wrappedDrawable = DrawableCompat.wrap(
					AppCompatResources.getDrawable(
						context,
						if (selectedIndex == position)
							R.drawable.ic_circle_check_24
						else R.drawable.ic_circle_24
					)!!
				)
				DrawableCompat.setTint(wrappedDrawable, colors[position].getColorPack().normal().asInt())
				drawableView.setImageDrawable(wrappedDrawable)
			}
		}
	}
}

