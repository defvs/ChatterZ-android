package dev.defvs.chatterz.autocomplete

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.otaliastudios.autocomplete.RecyclerViewPresenter
import dev.defvs.chatterz.R
import dev.defvs.chatterz.scrollTo


class ChatAutoCompletePresenter(
	context: Context,
	var emotes: List<CompletableTwitchEmote>
) : RecyclerViewPresenter<CompletableTwitchEmote>(context) {
	private lateinit var adapter: Adapter
	
	init {
		recyclerView?.isFocusable = false
	}
	
	override fun getPopupDimensions(): PopupDimensions {
		val dims = PopupDimensions()
		dims.width = 300
		dims.height = 800
		return dims
	}
	
	override fun instantiateAdapter(): RecyclerView.Adapter<*> {
		adapter = Adapter()
		return adapter
	}
	
	override fun onQuery(query: CharSequence?) {
		if (query.isNullOrEmpty()) {
			adapter.data = emotes
		} else {
			adapter.data = emotes.filter {
				it.name.contains(query.toString(), true)
			}.sortedBy { it.name }.sortedBy { it.name.length }
				.also { Log.d("ChatAutoComplete", "Found ${it.size} elements for query $query") }
		}
		adapter.selected = 0
		adapter.notifyDataSetChanged()
	}
	
	fun selectionDown() { adapter.selected++ }
	
	fun selectionUp() { adapter.selected-- }
	
	fun select() { dispatchClick(adapter.data?.get(adapter.selected)) }
	
	private inner class Adapter :
		RecyclerView.Adapter<Adapter.Holder>() {
		var selected: Int = 0
			set(value) {
				field = value.coerceAtLeast(0).coerceAtMost(
					data?.size?.minus(1)?.coerceAtLeast(0) ?: 0
				)
				this@ChatAutoCompletePresenter.recyclerView?.layoutManager?.scrollTo(
					context,
					(field - 1).coerceAtLeast(0)
				)
				notifyDataSetChanged()
			}
		var data: List<CompletableTwitchEmote>? = null
		
		private inner class Holder internal constructor(itemView: View) :
			RecyclerView.ViewHolder(itemView) {
			val root: View = itemView
			val emoteName: TextView = itemView.findViewById(R.id.emote_name)
		}
		
		override fun getItemCount(): Int {
			return if (data.isNullOrEmpty()) 1 else data!!.size
		}
		
		override fun onCreateViewHolder(
			parent: ViewGroup,
			viewType: Int
		): Holder {
			return Holder(
				LayoutInflater.from(context).inflate(R.layout.autocomplete_item, parent, false)
			)
		}
		
		override fun onBindViewHolder(
			holder: Holder,
			position: Int
		) {
			if (data.isNullOrEmpty() || position >= data!!.size) {
				holder.emoteName.text = context.getString(R.string.no_emotes_found)
				holder.root.setOnClickListener(null)
				holder.root.background = null
				return
			}
			try {
				val user = data!![position]
				holder.emoteName.text = data!![position].name
				holder.root.setOnClickListener { dispatchClick(user) }
				holder.root.background =
					if (selected == position) ColorDrawable(Color.parseColor("#22FFFFFF")) else null
			} catch (_: ArrayIndexOutOfBoundsException) {
			}
		}
	}
}