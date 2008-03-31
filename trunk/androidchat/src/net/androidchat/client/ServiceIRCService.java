package net.androidchat.client;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.*;

import android.os.Binder;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;

public class ServiceIRCService extends Service {
	private static Thread connection;

	private static Socket socket;
	public static BufferedWriter writer;
	public static BufferedReader reader;
	public static int state;
	private static String server = "irc.freenode.net";
	private static String nick = "AndroidChat";

	public static final int MSG_UPDATECHAN = 0;
	public static final int MSG_UPDATEPM = 1;
	public static final int MSG_CHANGECHAN = 2;
	public static final int MSG_DISCONNECT = 3;

	public static HashMap<String, ClassChannelContainer> channels;

	public static Handler ChannelViewHandler;

	// this is epic irc parsing.
	public static void GetLine(String line)
	{
		// rfc 2812
		// [:prefix] command|numeric [arg1, arg2...] :extargs

		String args, prefix, command;
		args = prefix = command = "";    	

		// pull off extended arguments first
		if (line.indexOf(":", 2) != -1)
			args = line.substring(line.indexOf(":", 2) + 1).trim();

		// if we have extended arguments, remove them from the parsing
		if (args.length() > 0)
			line = line.substring(0, line.length() - args.length());

		String[] toks = line.split(" "); // split by spaces.

		if (toks[0].startsWith(":")) // we have a prefix
		{
			prefix = toks[0].substring(1);
			command = toks[1];
		} else {
			prefix = null;
			command = toks[0];
		}    

		if(command.equals("331") || command.equals("332"))
		// :servername 331 yournick #channel :no topic
		// :servername 332 yournick #channel :topic here
		{       	
			ClassChannelContainer temp;
			String chan = toks[3].toLowerCase();
			if (channels.containsKey(chan))
			{
				temp = channels.get(chan);      		 
				temp.addLine("Topic for " + toks[3] + " is: " + args);
				temp.chantopic = args;
				if (ChannelViewHandler != null)
					Message.obtain(ChannelViewHandler, ServiceIRCService.MSG_UPDATECHAN, chan).sendToTarget();
			} // ignore topics for channels we aren't in
		} else
		if (command.equals("JOIN"))
		// User must have joined a channel
		{
			ClassChannelContainer temp;
			if (channels.containsKey(args.toLowerCase())) // existing channel?
			{
				temp = channels.get(args);
			} else {
				temp = new ClassChannelContainer();
				temp.channame = args;
				temp.addLine("Now talking on " + args + "...");
				channels.put(args.toLowerCase(), temp);
			}
		} else
		if (command.equals("PRIVMSG"))  
		// to a channel? 
		{
			ClassChannelContainer temp;
			String chan = toks[2].toLowerCase();
			
			if (channels.containsKey(chan)) // existing channel?
			{
				temp = channels.get(chan);
				temp.addLine("<" + toks[0].substring(1, toks[0].indexOf("!")) + "> " + args);
				if (ChannelViewHandler != null)
					Message.obtain(ChannelViewHandler, ServiceIRCService.MSG_UPDATECHAN, toks[2].toLowerCase()).sendToTarget();
			}
		}

	}

	public static void SendToChan(String chan, String what)
	{
		if (what.trim().equals("")) return;
		if (chan == null) 
		{
			// error about not being on a channel here
			return;
		}
		// PRIVMSG <target> :<message>
		try {
			String temp = "PRIVMSG " + chan + " :" + what + "\n";
			GetLine(":" + nick + "! " + temp);
			writer.write(temp);
			writer.flush();
			if (ChannelViewHandler != null)
				Message.obtain(ChannelViewHandler, ServiceIRCService.MSG_UPDATECHAN, chan).sendToTarget();

		} catch (IOException e) {
			e.printStackTrace();
		} catch (NullPointerException npe) {
			npe.printStackTrace();
		}
	}

	@Override
	protected void onCreate()
	{
		mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		// This is who should be launched if the user selects our persistent
		// notification.
		Intent intent = new Intent();
		intent.setClass(this, ActivityAndroidChatMain.class);

		channels = new HashMap<String, ClassChannelContainer>();

		// Display a notification about us starting.  We use both a transient
		// notification and a persistent notification in the status bar.
		mNM.notify(R.string.irc_started,
				new Notification(this,
						R.drawable.icon,
						getText(R.string.irc_started),
						null,
						getText(R.string.irc_started),
						getText(R.string.irc_started),
						intent,
						R.drawable.icon,
						"Android Chat",
						intent));

		connection = new Thread(new ThreadConnThread(server, nick, socket));
		connection.start();

		mNM.notifyWithText(R.string.irc_connected,
				getText(R.string.irc_connected),
				NotificationManager.LENGTH_SHORT,
				null);

	}

	@Override
	protected void onDestroy()
	{
		// Cancel the persistent notification.
		mNM.cancel(R.string.irc_started);

		connection.interrupt();
		state = 0;

		// Tell the user we stopped.
		mNM.notifyWithText(R.string.irc_stopped,
				getText(R.string.irc_stopped),
				NotificationManager.LENGTH_SHORT,
				null);
	}

	
	public IBinder getBinder()
	{
		return mBinder;
	}

	// This is the object that receives interactions from clients.  See
	// RemoteService for a more complete example.
	private final IBinder mBinder = new Binder() {
		@Override
		protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
		{
			return super.onTransact(code, data, reply, flags);
		}
	};

	private NotificationManager mNM;
}