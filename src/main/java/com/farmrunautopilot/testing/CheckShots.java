package com.farmrunautopilot.testing;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;

/**
 * Screenshots of the game at the moments a hand check is about (a visit to the tool leprechaun, "Inventory full", a bucket
 * line, arriving at a stop), so a run can be checked from the log and these instead of by hand (elzerma, 2026-10-29).
 * Developer client only. Each is named after its log line's time and kept in the plugin's folder under check-shots,
 * at most {@link #PER_SESSION} a session; those older than {@link #KEEP_DAYS} days are deleted at startup.
 */
@Slf4j
@Singleton
public class CheckShots
{
	static final int PER_SESSION = 30;
	static final int KEEP_DAYS = 3;

	private final DrawManager drawManager;
	private final ScheduledExecutorService executor;
	private final boolean developerMode;
	private volatile Filepath folder;
	private int taken;

	@Inject
	CheckShots(DrawManager drawManager, ScheduledExecutorService executor, @Named("developerMode") boolean developerMode)
	{
		this.drawManager = drawManager;
		this.executor = executor;
		this.developerMode = developerMode;
	}

	/** The plugin's folder is known: old screenshots are cleared in the background. */
	public void startUp(Filepath pluginDirectory)
	{
		if (!developerMode)
		{
			return;
		}
		folder = pluginDirectory.join("check-shots");
		taken = 0;
		executor.execute(this::deleteOld);
	}

	/** Capture the next frame, named "time stop - what" (client thread). */
	public void take(String what)
	{
		if (folder == null || taken >= PER_SESSION)
		{
			return;
		}
		taken++;
		final String name = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + " " + safe(what) + ".png";
		drawManager.requestNextFrameListener(image ->
		{
			final BufferedImage frame = ImageUtil.bufferedImageFromImage(image);
			executor.execute(() -> save(frame, name));
		});
	}

	private void save(BufferedImage frame, String name)
	{
		try
		{
			folder.createDirectories();
			try (OutputStream out = folder.join(name).openOutputStream())
			{
				ImageIO.write(frame, "png", out);
			}
			log.debug("Check screenshot: {}", name);
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("Check screenshot not saved: {}", name, e);
		}
	}

	/** The cleanup: screenshots older than {@link #KEEP_DAYS} days go. */
	private void deleteOld()
	{
		if (!folder.isDirectory())
		{
			return;
		}
		final long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(KEEP_DAYS);
		try (Stream<Filepath> files = folder.walk(1))
		{
			final List<Filepath> old = files.filter(f -> f.isFile() && modified(f) < cutoff).collect(Collectors.toList());
			for (Filepath f : old)
			{
				f.deleteIfExists();
			}
			if (!old.isEmpty())
			{
				log.debug("Deleted {} check screenshots older than {} days", old.size(), KEEP_DAYS);
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("Old check screenshots not cleared", e);
		}
	}

	private static long modified(Filepath file)
	{
		try
		{
			final FileTime time = file.getLastModifiedTime();
			return time.toMillis();
		}
		catch (IOException e)
		{
			return Long.MAX_VALUE;
		}
	}

	/** A file name the folder accepts: no path or reserved characters, not ending in a dot or space. */
	public static String safe(String text)
	{
		final String cleaned = text.replaceAll("[<>:\"/\\\\|?*~]", "").replaceAll("\\s+", " ").trim();
		final String cut = cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
		return cut.replaceAll("[. ]+$", "");
	}
}
