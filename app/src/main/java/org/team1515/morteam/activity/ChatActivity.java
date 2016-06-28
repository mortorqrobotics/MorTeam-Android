package org.team1515.morteam.activity;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.Html;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.NetworkImageView;
import com.android.volley.toolbox.Volley;
import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.engineio.client.Transport;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Manager;
import com.github.nkzawa.socketio.client.Socket;

import net.team1515.morteam.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.team1515.morteam.MorTeam;
import org.team1515.morteam.entity.Message;
import org.team1515.morteam.entity.PictureCallBack;
import org.team1515.morteam.entity.User;
import org.team1515.morteam.network.CookieRequest;

import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class ChatActivity extends AppCompatActivity {

    SharedPreferences preferences;
    RequestQueue queue;

    private String chatName;
    private String chatId;
    private boolean isGroup;

    private RecyclerView messageList;
    private MessageAdapter messageAdapter;
    private LinearLayoutManager layoutManager;
    private boolean loading = false;
    private boolean canLoadMore = true;
    private int firstItem, visibleItemCount, totalItemCount;

    private Socket socket;

    private boolean isClearingText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = getSharedPreferences(null, 0);
        queue = Volley.newRequestQueue(this);

        setContentView(R.layout.activity_chat);
        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        //Get messages
        Intent intent = getIntent();
        chatName = intent.getStringExtra("name");
        chatId = intent.getStringExtra("_id");
        isGroup = intent.getBooleanExtra("isGroup", false);

        messageList = (RecyclerView) findViewById(R.id.chat_messagelist);
        messageAdapter = new MessageAdapter();
        layoutManager = new LinearLayoutManager(this);
        layoutManager.setReverseLayout(true);
        messageList.setLayoutManager(layoutManager);
        messageList.setAdapter(messageAdapter);


        messageList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy < 0) //check for scroll up
                {
                    if (!loading && canLoadMore) {
                        visibleItemCount = layoutManager.getChildCount();
                        totalItemCount = layoutManager.getItemCount();
                        firstItem = layoutManager.findFirstVisibleItemPosition();

                        if (visibleItemCount + firstItem >= totalItemCount - 2) {
                            loading = true;
                            messageAdapter.getChats();
                        }
                    }
                }
            }
        });

        isClearingText = false;
        final EditText messageBox = (EditText) findViewById(R.id.chat_message);
        messageBox.addTextChangedListener(new TextWatcher() {
            Date lastTypeTime = null;

            @Override
            public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
                if (!isClearingText) {
                    try {
                        JSONObject typingObject = new JSONObject();
                        typingObject.put("chat_id", chatId);
                        socket.emit("start typing", typingObject);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                lastTypeTime = new Date();
            }

            @Override
            public void afterTextChanged(Editable arg0) {

            }

            @Override
            public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
                // Dispatch after done typing (1 sec after)
                Timer timer = new Timer();
                TimerTask timerTask = new TimerTask() {
                    @Override
                    public void run() {
                        Date runTime = new Date();

                        if ((lastTypeTime.getTime() + 1000) <= runTime.getTime()) {
                            try {
                                JSONObject typingObject = new JSONObject();
                                typingObject.put("chat_id", chatId);
                                socket.emit("stop typing", typingObject);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                        }
                    }
                };
                timer.schedule(timerTask, 1000);
            }
        });


        try {
            socket = IO.socket("http://www.morteam.com");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        final String sessionId = preferences.getString(CookieRequest.SESSION_COOKIE, "");

        socket.io().on(Manager.EVENT_TRANSPORT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Transport transport = (Transport) args[0];
                transport.on(Transport.EVENT_REQUEST_HEADERS, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> headers = (Map<String, String>) args[0];

                        // set header
                        //Insert session-id cookie into header
                        if (sessionId.length() > 0) {
                            StringBuilder builder = new StringBuilder();
                            builder.append(CookieRequest.SESSION_COOKIE);
                            builder.append("=");
                            builder.append(sessionId);
                            if (headers.containsKey(CookieRequest.COOKIE_KEY)) {
                                builder.append("; ");
                                builder.append(headers.get(CookieRequest.COOKIE_KEY));
                            }
                            headers.put(CookieRequest.COOKIE_KEY, builder.toString());
                        }
                    }
                }).on(Transport.EVENT_RESPONSE_HEADERS, new Emitter.Listener() {
                    @Override
                    public void call(Object... args) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> headers = (Map<String, String>) args[0];
                        //No headers to get here at the moment
                    }
                });
            }
        });
        socket = socket.connect();

        socket.emit("get clients");
        socket.on("get clients", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                //TODO: Get online clients
            }
        });

        socket.on("message", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    JSONObject messageObject = new JSONObject(args[0].toString());

                    String firstName = messageObject.getString("author_fn");
                    String lastName = messageObject.getString("author_ln");
                    String content = messageObject.getString("content");
                    String date = messageObject.getString("timestamp");
                    String chatId = messageObject.getString("chat_id");
                    String profPicPath = messageObject.getString("author_profpicpath") + "-60";
                    profPicPath = profPicPath.replace(" ", "+");

                    NotificationCompat.Builder builder = new NotificationCompat.Builder(ChatActivity.this);
                    builder.setSmallIcon(R.mipmap.ic_launcher);
                    builder.setContentTitle("New Message");
                    builder.setContentText(content);

                    Intent notificationIntent = new Intent(ChatActivity.this, ChatActivity.class);
                    notificationIntent.putExtra("firstname", firstName);
                    notificationIntent.putExtra("lastname", lastName);
                    notificationIntent.putExtra("_id", chatId);
                    notificationIntent.putExtra("isGroup", false);

                    TaskStackBuilder stackBuilder = TaskStackBuilder.create(ChatActivity.this);
                    stackBuilder.addParentStack(ChatActivity.class);
                    stackBuilder.addNextIntent(notificationIntent);

                    PendingIntent pendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
                    builder.setContentIntent(pendingIntent);

                    NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
//                    notificationManager.notify(1, builder.build());


                    Intent intent = new Intent("message");
                    intent.putExtra("firstname", firstName);
                    intent.putExtra("lastname", lastName);
                    intent.putExtra("content", content);
                    intent.putExtra("date", date);
                    intent.putExtra("chatId", chatId);
                    intent.putExtra("profPicPath", profPicPath);
                    LocalBroadcastManager.getInstance(ChatActivity.this).sendBroadcast(intent);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                messageAdapter.addMessage(
                        intent.getStringExtra("firstname"),
                        intent.getStringExtra("lastname"),
                        intent.getStringExtra("content"),
                        intent.getStringExtra("date"),
                        intent.getStringExtra("chatId"),
                        intent.getStringExtra("profPicPath"),
                        false
                );

                messageAdapter.scrollToBottom();
            }
        }, new IntentFilter("message"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        socket.disconnect();
        socket.off();
    }

    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    public void sendClick(View view) {
        //Disable send button until message sent
        final Button sendButton = (Button) findViewById(R.id.chat_send);
        sendButton.setClickable(false);

        final EditText messageText = (EditText) findViewById(R.id.chat_message);
        final String messageContent = messageText.getText().toString();

        if (!messageContent.isEmpty()) {

            Map<String, String> params = new HashMap<>();
            params.put("chat_id", chatId);
            params.put("content", messageContent);

            CookieRequest sendRequest = new CookieRequest(Request.Method.POST, "/f/sendMessage",
                    params,
                    preferences,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            try {
                                JSONObject typingObject = new JSONObject();
                                typingObject.put("chat_id", chatId);
                                socket.emit("stop typing", typingObject);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                            JSONObject messageObject = new JSONObject();
                            try {
                                messageObject.put("chat_id", chatId);
                                messageObject.put("content", messageContent);
                                if (isGroup) {
                                    messageObject.put("type", "group");
                                    messageObject.put("chat_name", chatName);
                                } else {
                                    messageObject.put("type", "private");
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            socket.emit("message", messageObject);

                            isClearingText = true;
                            messageText.setText("");
                            isClearingText = false;

                            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
                            System.out.println(df.format(new Date()));
                            messageAdapter.addMessage(
                                    preferences.getString("firstname", ""),
                                    preferences.getString("lastname", ""),
                                    messageContent,
                                    df.format(new Date()),
                                    chatId,
                                    preferences.getString("profpicpath", "") + "-60",
                                    true
                            );
                            messageAdapter.scrollToBottom();
                            sendButton.setClickable(true);
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    error.printStackTrace();
                    sendButton.setClickable(true);
                }
            });
            queue.add(sendRequest);
        }
    }

    public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {
        private List<Message> messages;

        public MessageAdapter() {
            messages = new ArrayList<>();
            getChats();
        }

        public void getChats() {
            Map<String, String> params = new HashMap<>();
            params.put("chat_id", chatId);
            final int skip;
            if (!messages.isEmpty()) {
                skip = ((messages.size() - 1) / 20 + 1) * 20;
                params.put("skip", skip + "");
            } else {
                skip = 0;
            }
            CookieRequest messageRequest = new CookieRequest(
                    Request.Method.POST,
                    "/f/loadMessagesForChat",
                    params,
                    preferences,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            try {
                                JSONArray messageArray = new JSONArray(response);
                                if (skip - messages.size() >= messageArray.length()) {
                                    //No more messages are left in the chat - cease fire(ing request!)
                                    canLoadMore = false;
                                } else {
                                    for (int i = (skip == 0 ? 0 : skip - messages.size()); i < messageArray.length(); i++) {
                                        JSONObject messageObject = messageArray.getJSONObject(i);
                                        String id = messageObject.getString("_id");
                                        String content = messageObject.getString("content");
                                        String date = messageObject.getString("timestamp");

                                        JSONObject authorObject = messageObject.getJSONObject("author");
                                        String firstName = authorObject.getString("firstname");
                                        String lastName = authorObject.getString("lastname");
                                        String profPicPath = authorObject.getString("profpicpath") + "-60";
                                        profPicPath = profPicPath.replace(" ", "+");
                                        boolean isMyChat = false;
                                        if (authorObject.getString("_id").equals(preferences.getString("_id", ""))) {
                                            isMyChat = true;
                                        }

                                        final Message message = new Message(new User(firstName, lastName, null, profPicPath), content, date, chatId, isMyChat);

                                        if (skip <= 0) {
                                            messages.add(message);
                                        }
                                    }
                                    notifyDataSetChanged();
                                }
                                loading = false;
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            System.out.println("ERROR: " + error);
                        }
                    }
            );
            queue.add(messageRequest);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            RelativeLayout relativeLayout = (RelativeLayout) LayoutInflater.from(parent.getContext()).inflate(R.layout.list_message, parent, false);
            ViewHolder viewHolder = new ViewHolder(relativeLayout);
            return viewHolder;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            final Message currentMessage = messages.get(position);

            TextView message = (TextView) holder.relativeLayout.findViewById(R.id.messagelist_message);
            message.setMovementMethod(LinkMovementMethod.getInstance());

            final TextView date = (TextView) holder.relativeLayout.findViewById(R.id.messagelist_date);
            date.setText(currentMessage.getDate());

            CardView cardView = (CardView) holder.relativeLayout.findViewById(R.id.messagelist_cardview);
            final NetworkImageView messagePic = (NetworkImageView) holder.relativeLayout.findViewById(R.id.messagelist_pic);

            View.OnClickListener dateClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (date.getVisibility() == View.GONE) {
                        date.setVisibility(View.VISIBLE);
                    } else {
                        date.setVisibility(View.GONE);
                    }
                }
            };
            cardView.setOnClickListener(dateClickListener);
            message.setOnClickListener(dateClickListener);

            SpannableStringBuilder messageString = new SpannableStringBuilder();
            SpannableString contentString = new SpannableString(Html.fromHtml(currentMessage.getContent()));

            if (currentMessage.isMyMessage) {
                messagePic.setVisibility(View.INVISIBLE);
                messagePic.getLayoutParams().width = 0;

                //Change background color and align to right
                cardView.setCardBackgroundColor(Color.argb(255, 255, 197, 71));

                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) cardView.getLayoutParams();
                params.addRule(RelativeLayout.ALIGN_PARENT_LEFT, 0);
                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            } else {
                SpannableString nameString = new SpannableString(currentMessage.getFirstName() + ": ");
                nameString.setSpan(new StyleSpan(Typeface.BOLD), 0, nameString.length(), 0);
                messageString.append(nameString);

                MorTeam.setNetworkImage(currentMessage.getProfPicPath(), messagePic);
                messagePic.setVisibility(View.VISIBLE);
                messagePic.getLayoutParams().width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, getResources().getDisplayMetrics());


                cardView.setCardBackgroundColor(Color.WHITE);

                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) cardView.getLayoutParams();
                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, 0);
                params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            }

            messageString.append(contentString);
            message.setText(messageString, TextView.BufferType.SPANNABLE);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public RelativeLayout relativeLayout;

            public ViewHolder(RelativeLayout relativeLayout) {
                super(relativeLayout);
                this.relativeLayout = relativeLayout;
            }
        }

        public void addMessage(String firstName, String lastName, String content, String date, String chatId, String profPicPath, boolean isMyChat) {
            messages.add(0, new Message(new User(firstName, lastName, null, profPicPath), content, date, chatId, isMyChat));
            notifyDataSetChanged();
        }

        public void scrollToBottom() {
            messageList.smoothScrollToPosition(0);
        }
    }
}