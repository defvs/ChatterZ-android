package dev.defvs.chatterz

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.otaliastudios.autocomplete.Autocomplete
import com.otaliastudios.autocomplete.AutocompleteCallback
import com.otaliastudios.autocomplete.CharPolicy
import dev.defvs.chatterz.MainActivity.Companion.chatClient
import dev.defvs.chatterz.autocomplete.ChatAutoCompletePresenter
import dev.defvs.chatterz.autocomplete.CompletableTwitchEmote
import dev.defvs.chatterz.settings.SettingsActivity
import dev.defvs.chatterz.themes.ThemedActivity
import dev.defvs.chatterz.twitch.ChatClient
import dev.defvs.chatterz.twitch.TwitchMessage
import io.multimoon.colorful.Colorful
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import dev.defvs.chatterz.MainActivity.Companion.sharedPreferences as preferences


class MainActivity : ThemedActivity() {
	private lateinit var chatRecyclerView: RecyclerView
	private lateinit var chatViewAdapter: RecyclerView.Adapter<*>
	private lateinit var chatViewManager: RecyclerView.LayoutManager
	
	private val sharedPreferencesListener =
		SharedPreferences.OnSharedPreferenceChangeListener { _, s ->
			when (s) {
				"checkered_lines" -> redrawChat()
				"dark_theme" -> Colorful().edit()
					.setDarkTheme(sharedPreferences.getBoolean("dark_theme", true))
					.apply(this) {
						this.recreate()
					}
			}
		}
	
	private fun redrawChat() {
		chatRecyclerView.adapter = chatViewAdapter
	}
	
	private val messages: ArrayList<TwitchMessage> = arrayListOf()
	
	private lateinit var autoComplete: Autocomplete<CompletableTwitchEmote>
	private val autoCompletePresenter = ChatAutoCompletePresenter(this, listOf())
	private var channelEmotes: List<CompletableTwitchEmote>? = null
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		setSupportActionBar(toolbar)
		
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
		
		chatViewManager = LinearLayoutManager(this)
		chatViewAdapter = ChatAdapter(messages, this)
		chatRecyclerView = chatRecycler.apply {
			setHasFixedSize(true)
			descendantFocusability
			layoutManager = chatViewManager
			adapter = chatViewAdapter
		}
		
		sendButton.setOnClickListener { sendMessage(messageBox.text.toString()) }
		messageBox.setOnEditorActionListener { textView, id, _ ->
			return@setOnEditorActionListener when (id) {
				EditorInfo.IME_ACTION_SEND -> {
					sendMessage(textView.text.toString())
					true
				}
				else -> false
			}
		}
		messageBox.setOnKeyListener { textView, id, event ->
			return@setOnKeyListener if (event.action == KeyEvent.ACTION_DOWN) when (id) {
				in listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER) -> {
					if (autoComplete.isPopupShowing) autoCompletePresenter.select()
					else sendMessage((textView as TextView).text.toString())
					
					true
				}
				KeyEvent.KEYCODE_DPAD_UP -> {
					autoCompletePresenter.selectionUp(); true
				}
				KeyEvent.KEYCODE_DPAD_DOWN -> {
					autoCompletePresenter.selectionDown(); true
				}
				else -> false
			} else false
		}
		
		sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferencesListener)
		
		// Autocomplete
		
		autoComplete = Autocomplete.on<CompletableTwitchEmote>(messageBox)
			.with(CharPolicy(':', true))
			.with(autoCompletePresenter)
			.with(ColorDrawable(Color.parseColor("#77000000")))
			.with(object : AutocompleteCallback<CompletableTwitchEmote> {
				override fun onPopupItemClicked(
					editable: Editable?,
					item: CompletableTwitchEmote?
				): Boolean {
					if (editable == null || item == null) return false
					val range =
						CharPolicy.getQueryRange(editable)?.let { it[0]..it[1] } ?: return false
					editable.replace(range.first - 1, range.last, item.name)
					return true
				}
				
				override fun onPopupVisibilityChanged(shown: Boolean) {}
			})
			.build()
	}
	
	override fun onSaveInstanceState(outState: Bundle) {
		outState.run {
			putParcelableArrayList("messages", messages)
		}
		
		super.onSaveInstanceState(outState)
	}
	
	override fun onRestoreInstanceState(savedInstanceState: Bundle) {
		savedInstanceState.getParcelableArrayList<TwitchMessage>("messages")?.let {
			messages.addAll(it)
		}
		
		super.onRestoreInstanceState(savedInstanceState)
	}
	
	override fun onStop() {
		chatClient?.shutdown()
		super.onStop()
	}
	
	override fun onRestart() {
		super.onRestart()
		chatClient?.connect()
	}
	
	override fun onDestroy() {
		chatClient?.shutdown()
		super.onDestroy()
	}
	
	override fun onResume() {
		super.onResume()
		chatClient?.connect()
	}
	
	private var menu: Menu? = null
	override fun onCreateOptionsMenu(menu: Menu?): Boolean {
		menuInflater.inflate(R.menu.main_menu, menu)
		this.menu = menu
		return true
	}
	
	override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
		R.id.settings -> {
			startActivity(Intent(this, SettingsActivity::class.java))
			overridePendingTransition(R.anim.slide_in_right, android.R.anim.fade_out)
			true
		}
		R.id.connect_chat -> {
			val token = sharedPreferences.getString("twitch_token", null)
			val username = sharedPreferences.getString("twitch_username", null)
			val channel = sharedPreferences.getString("twitch_default_channel", username)
				.let { if (it.isNullOrBlank()) username else it }
			val twitchAPIKey = resources.getString(R.string.twitch_client_id)
			
			supportActionBar?.title = getString(R.string.connecting_to_with_emoji, channel)
			disconnectedHint.text = getString(R.string.connecting_to, channel)
			disconnectedHint.visibility = View.VISIBLE
			
			if (!(token.isNullOrBlank() || username.isNullOrBlank() || channel.isNullOrBlank())) {
				GlobalScope.launch {
					chatClient?.shutdown()
					
					try {
						chatClient = ChatClient(
							username,
							token,
							twitchAPIKey,
							channel
						).apply {
							messageReceivedEvent = {
								runOnUiThread {
									onMessage(it)
								}
							}
							
							disconnectedEvent = {
								runOnUiThread {
									supportActionBar?.title =
										getString(R.string.disconnected_from, channel)
									menu?.findItem(R.id.connect_chat)?.apply {
										setIcon(R.drawable.ic_play_circle_filled_24)
										title = getString(R.string.action_connect)
									}
									messageBox.isEnabled = false
									disconnectedHint.text = getString(R.string.disconnected)
									disconnectedHint.visibility = View.VISIBLE
								}
							}
						}
						runOnUiThread {
							menu?.findItem(R.id.connect_chat)?.apply {
								setIcon(R.drawable.ic_refresh_24)
								title = getString(R.string.action_reconnect)
							}
							messageBox.isEnabled = true
							disconnectedHint.text = getString(R.string.loading_emotes)
							
							supportActionBar?.title = getString(R.string.loading_emotes_with_emoji)
						}
						channelEmotes = ChatClient.getUserId(twitchAPIKey, channel)
							?.let {
								CompletableTwitchEmote.getAllEmotes(
									it,
									twitchAPIKey,
									token,
									username
								)
							}
						autoCompletePresenter.emotes = channelEmotes ?: listOf()
						runOnUiThread {
							supportActionBar?.title = getString(R.string.connected_to, channel)
							disconnectedHint.visibility = View.GONE
						}
					} catch (e: IOException) {
						Log.e("ChatClient", "Client init failed")
					}
				}
			}
			
			
			true
		}
		else -> super.onOptionsItemSelected(item)
	}
	
	private fun onMessage(message: TwitchMessage) {
		messages.add(message)
		chatViewAdapter.notifyItemInserted(messages.size - 1)
		chatViewManager.scrollTo(this, messages.size - 1)
	}
	
	private fun sendMessage(message: String) {
		if (message.isBlank() || chatClient == null) return
		
		chatClient!!.sendMessage(message)
		
		messages.add(TwitchMessage(chatClient!!.username, message))
		chatViewAdapter.notifyItemInserted(messages.size - 1)
		chatViewManager.scrollTo(this, messages.size - 1)
		
		messageBox.clear()
	}
	
	companion object {
		lateinit var sharedPreferences: SharedPreferences
		
		var chatClient: ChatClient? = null
	}
}

class ChatAdapter(private val messages: ArrayList<TwitchMessage>, private val context: Context) :
	RecyclerView.Adapter<ChatAdapter.ViewHolder>() {
	
	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
		ViewHolder(
			LayoutInflater.from(parent.context)
				.inflate(R.layout.chat_line, parent, false)
		)
	
	override fun getItemCount() = messages.size
	
	override fun onBindViewHolder(holder: ViewHolder, i: Int) {
		with(messages[i]) { holder.messageText.text = "${sender.displayName}: $message" }
		GlobalScope.launch {
			try {
				val spannable = chatClient?.getMessageSpannable(context, messages[i])
				withContext(Dispatchers.Main) {
					spannable?.let { holder.messageText.text = it }
				}
			} catch (e: Exception) {
				Log.w("SpannableLoader", "Emotes and badges load failed", e)
			}
		}
		if (i % 2 == 0 && preferences.getBoolean("checkered_lines", false))
			holder.itemView.setBackgroundColor(
				Color.parseColor(
					if (Colorful().getDarkTheme()) "#22FFFFFF"
					else "#22000000"
				)
			)
		else holder.itemView.setBackgroundColor(Color.parseColor("#00000000"))
	}
	
	class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val messageText: TextView = view.findViewById(R.id.chatMessage)
	}
	
}