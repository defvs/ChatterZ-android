package dev.defvs.chatterz

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.defvs.chatterz.settings.SettingsActivity
import dev.defvs.chatterz.themes.ThemedActivity
import dev.defvs.chatterz.twitch.ChatClient
import dev.defvs.chatterz.twitch.TwitchMessage
import io.multimoon.colorful.Colorful
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import dev.defvs.chatterz.MainActivity.Companion.sharedPreferences as preferences

class MainActivity : ThemedActivity() {
	private lateinit var chatRecyclerView: RecyclerView
	private lateinit var chatViewAdapter: RecyclerView.Adapter<*>
	private lateinit var chatViewManager: RecyclerView.LayoutManager
	
	private var chatClient: ChatClient? = null
	
	private val sharedPreferencesListener =
		SharedPreferences.OnSharedPreferenceChangeListener { _, s ->
			when (s) {
				"checkered_lines" -> redrawChat()
			}
		}
	
	private fun redrawChat() {
		chatRecyclerView.adapter = chatViewAdapter
	}
	
	private val messages: ArrayList<TwitchMessage> = arrayListOf()
	
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		setSupportActionBar(toolbar)
		
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
		
		chatViewManager = LinearLayoutManager(this)
		chatViewAdapter = ChatAdapter(messages)
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
			return@setOnKeyListener when {
				id in listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)
						&& event.hasNoModifiers() && event.action == KeyEvent.ACTION_DOWN -> {
					sendMessage((textView as TextView).text.toString())
					true
				}
				else -> false
			}
		}
		
		sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferencesListener)
	}
	
	override fun onCreateOptionsMenu(menu: Menu?): Boolean {
		menuInflater.inflate(R.menu.main_menu, menu)
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
			
			if (!(token.isNullOrBlank() || username.isNullOrBlank() || channel.isNullOrBlank())) {
				GlobalScope.launch {
					chatClient?.shutdown()
					
					chatClient = ChatClient(username, token, channel).apply {
						messageReceivedEvent = {
							runOnUiThread {
								onMessage(it)
							}
						}
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
	}
}

class ChatAdapter(private val messages: ArrayList<TwitchMessage>) :
	RecyclerView.Adapter<ChatAdapter.ViewHolder>() {
	
	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
		ViewHolder(
			LayoutInflater.from(parent.context)
				.inflate(R.layout.chat_line, parent, false)
		)
	
	override fun getItemCount() = messages.size
	
	override fun onBindViewHolder(holder: ViewHolder, i: Int) {
		with(messages[i]) {
			holder.senderText.text = sender.displayName ?: sender.username
			if (sender.color == null)
				holder.senderText.setTextColor(holder.messageText.textColors)
			else sender.color!!.let {
				holder.senderText.setTextColor(
					if (Colorful().getDarkTheme()) it.lightenColor() else it.darkenColor()
				)
			}
			holder.messageText.text = message
			if (i % 2 == 0 && preferences.getBoolean("checkered_lines", false))
				holder.itemView.setBackgroundColor(
					Color.parseColor(
						if (Colorful().getDarkTheme()) "#22FFFFFF"
						else "#22000000"
					)
				)
			else holder.itemView.setBackgroundColor(Color.parseColor("#00000000"))
		}
	}
	
	class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		val senderText: TextView = view.findViewById(R.id.chatSender)
		val messageText: TextView = view.findViewById(R.id.chatMessage)
	}
	
}