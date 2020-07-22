package dev.defvs.chatterz

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.zsmb.materialdrawerkt.builders.drawer
import co.zsmb.materialdrawerkt.builders.footer
import co.zsmb.materialdrawerkt.draweritems.badgeable.primaryItem
import co.zsmb.materialdrawerkt.draweritems.divider
import co.zsmb.materialdrawerkt.imageloader.drawerImageLoader
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.materialdrawer.util.DrawerUIUtils
import com.otaliastudios.autocomplete.Autocomplete
import com.otaliastudios.autocomplete.AutocompleteCallback
import com.otaliastudios.autocomplete.CharPolicy
import dev.defvs.chatterz.MainActivity.Companion.chatClient
import dev.defvs.chatterz.autocomplete.ChatAutoCompletePresenter
import dev.defvs.chatterz.autocomplete.CompletableTwitchEmote
import dev.defvs.chatterz.settings.SettingsActivity
import dev.defvs.chatterz.themes.ThemedActivity
import dev.defvs.chatterz.twitch.ChatClient
import dev.defvs.chatterz.twitch.TwitchAPI
import dev.defvs.chatterz.twitch.TwitchAPI.getUserId
import dev.defvs.chatterz.twitch.TwitchMessage
import io.multimoon.colorful.Colorful
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt
import dev.defvs.chatterz.MainActivity.Companion.sharedPreferences as preferences


class MainActivity : ThemedActivity() {
	private lateinit var chatRecyclerView: RecyclerView
	private lateinit var chatViewAdapter: RecyclerView.Adapter<*>
	private lateinit var chatViewManager: RecyclerView.LayoutManager
	
	private val clientId: String
		get() = getString(R.string.twitch_client_id)
	
	private val sharedPreferencesListener =
		SharedPreferences.OnSharedPreferenceChangeListener { pref, s ->
			when (s) {
				"checkered_lines", "textsize_multiplier" -> redrawChat()
				"dark_theme" -> Colorful().edit()
					.setDarkTheme(sharedPreferences.getBoolean("dark_theme", true))
					.apply(this) {
						this.recreate()
					}
				"twitch_last_channel", "twitch_token" -> {
					setupDrawer(
						!pref.getString("twitch_token", null).isNullOrBlank(),
						!pref.getString("twitch_last_channel", null).isNullOrBlank()
					)
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
		supportActionBar?.setHomeButtonEnabled(true)
		
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
		
		chatViewManager = LinearLayoutManager(this)
		chatViewAdapter =
			ChatAdapter(messages, this) { chatViewManager.scrollTo(this, messages.size - 1) }
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
		
		// Twitch Login Intent
		handleIntent(intent)
		
		// Drawer
		setupDrawer(
			!sharedPreferences.getString("twitch_token", null).isNullOrBlank(),
			!sharedPreferences.getString("twitch_last_channel", null).isNullOrBlank()
		).openDrawer()
	}
	
	private fun showSnackbar(messageRes: Int, actionRes: Int? = null, action: ((View) -> Unit)? = null) =
		Snackbar.make(contentLayout, messageRes, Snackbar.LENGTH_LONG).apply {
			if (actionRes != null && action != null)
				setAction(actionRes, action)
		}.show()
	
	private fun showLoggedOffSnackbar() = showSnackbar(R.string.not_logged_in, R.string.action_login) { openTwitchLogin() }
	
	@Suppress("RedundantCompanionReference")
	private fun setupDrawer(enableConnect: Boolean = false, enableConnectLast: Boolean = false) =
		drawer {
			toolbar = this@MainActivity.toolbar
			primaryItem(
				if (enableConnect) "Logged in as ${Companion.sharedPreferences.getString("twitch_username", null)}"
				else "Logged out"
			) {
				selectable = false
				selected = false
			}
			divider {}
			primaryItem(getString(R.string.action_connect_self)) {
				selectable = false
				selected = false
				enabled = enableConnect
				onClick { _ ->
					connectToOwnChannel()
					false
				}
			}
			primaryItem(getString(R.string.action_connect_last)) {
				selectable = false
				selected = false
				enabled = enableConnect && enableConnectLast
				onClick { _ ->
					connectToLastOrOwn()
					false
				}
			}
			primaryItem("Connect to...") {
				enabled = enableConnect
				onClick { _ ->
					openChannelConnectDialog()
					false
				}
			}
			
			footer {
				primaryItem("Login / Change account") {
					onClick { _ ->
						openTwitchLogin()
						false
					}
				}
				divider {}
				primaryItem("Settings") {
					onClick { _ -> openSettings(); false }
				}
			}
		}
	
	class ChannelConnectDialog(private val callback: (String) -> Unit) : DialogFragment() {
		override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
			return activity?.let {
				val view = requireActivity().layoutInflater.inflate(R.layout.edittext_dialog, null)
				AlertDialog.Builder(it).setView(view)
					.setTitle(getString(R.string.connect_to))
					.setPositiveButton(R.string.action_connect) { _, _ ->
						callback(view.findViewById<EditText>(R.id.textField).text.toString())
						dialog?.cancel()
					}
					.setNegativeButton(android.R.string.cancel) { _, _ ->
						dialog?.cancel()
					}
					.create()
			} ?: throw IllegalStateException("Activity should not be null")
		}
	}
	
	private fun openChannelConnectDialog() {
		ChannelConnectDialog(::connectToChannel).show(supportFragmentManager, "channelConnect")
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
	
	private fun connectToOwnChannel() = sharedPreferences.getString("twitch_username", null).nullIfEmpty()?.let { connectToChannel(it) }
	
	private fun connectToLastChannel() = sharedPreferences.getString("twitch_last_channel", null).nullIfEmpty()?.let { connectToChannel(it) }
	
	private fun connectToLastOrOwn() = connectToLastChannel() ?: connectToOwnChannel() ?: showSnackbar(
		R.string.empty_username,
		R.string.action_login
	) { openTwitchLogin() }
	
	private fun connectToChannel(channel: String) {
		val token = sharedPreferences.getString("twitch_token", null)
		val username = sharedPreferences.getString("twitch_username", null)
		val twitchAPIKey = clientId
		
		supportActionBar?.title = getString(R.string.connecting_to_with_emoji, channel)
		disconnectedHint.text = getString(R.string.connecting_to, channel)
		disconnectedHint.visibility = View.VISIBLE
		
		sharedPreferences.edit().putString("twitch_last_channel", channel).apply()
		
		if (!(token.isNullOrBlank() || username.isNullOrBlank() || channel.isBlank())) {
			messages.clear()
			chatViewAdapter.notifyDataSetChanged()
			
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
									title = getString(R.string.action_connect_last)
								}
								messageBox.isEnabled = false
								disconnectedHint.text = getString(R.string.disconnected)
								showSnackbar(R.string.disconnected)
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
					channelEmotes = getUserId(twitchAPIKey, channel)
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
					showSnackbar(R.string.error_network)
					runOnUiThread {
						supportActionBar?.title =
							getString(R.string.connection_failed)
						menu?.findItem(R.id.connect_chat)?.apply {
							setIcon(R.drawable.ic_play_circle_filled_24)
							title = getString(R.string.action_connect_last)
						}
						messageBox.isEnabled = false
						disconnectedHint.text = getString(R.string.connection_failed)
						disconnectedHint.visibility = View.VISIBLE
					}
				}
			}
		} else showLoggedOffSnackbar()
	}
	
	private fun openSettings() {
		startActivity(Intent(this, SettingsActivity::class.java))
		overridePendingTransition(R.anim.slide_in_right, android.R.anim.fade_out)
	}
	
	override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
		R.id.settings -> {
			openSettings()
			true
		}
		R.id.connect_chat -> {
			connectToLastOrOwn()
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
	
	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		handleIntent(intent)
	}
	
	private fun handleIntent(intent: Intent) {
		if (intent.action == Intent.ACTION_VIEW) {
			val data = intent.data?.fragment?.split('&')?.map {
				it.split('=').let { it[0] to it[1] }
			}?.toMap() ?: mapOf()
			
			data["access_token"]?.let { token ->
				preferences.edit().putString("twitch_token", token).apply()
				GlobalScope.launch {
					val username =
						TwitchAPI.getUsername(clientId, token)
					preferences.edit().putString("twitch_username", username).apply()
				}
			} ?: showSnackbar(R.string.error_login)
		}
	}
	
	private fun openTwitchLogin() {
		val url = "https://id.twitch.tv/oauth2/authorize" +
				"?client_id=${clientId}" +
				"&redirect_uri=https://chatterz.live/twitch-oauth" +
				"&response_type=token" +
				"&scope=user_read chat:edit chat:read" +
				"&force_verify=true"
		startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
	}
	
	companion object {
		lateinit var sharedPreferences: SharedPreferences
		
		var chatClient: ChatClient? = null
	}
}

class ChatAdapter(
	private val messages: ArrayList<TwitchMessage>,
	private val context: Context,
	private val runScrolling: () -> Unit
) :
	RecyclerView.Adapter<ChatAdapter.ViewHolder>() {
	
	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
		ViewHolder(
			LayoutInflater.from(parent.context)
				.inflate(R.layout.chat_line, parent, false)
		)
	
	override fun getItemCount() = messages.size
	
	override fun onBindViewHolder(holder: ViewHolder, i: Int) {
		holder.messageText.setTextSize(
			TypedValue.COMPLEX_UNIT_SP,
			(14.0f) * (preferences.getInt("textsize_multiplier", 100) / 100f)
		)
		with(messages[i]) { holder.messageText.text = "${sender.displayName}: $message" }
		GlobalScope.launch {
			try {
				val spannable = chatClient?.getMessageSpannable(
					context,
					messages[i],
					size = (56 * (preferences.getInt(
						"textsize_multiplier",
						100
					) / 100f)).roundToInt()
				)
				withContext(Dispatchers.Main) {
					spannable?.let { holder.messageText.text = it }
					runScrolling()
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