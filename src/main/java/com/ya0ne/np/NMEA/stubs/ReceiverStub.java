package com.ya0ne.np.NMEA.stubs;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import com.ya0ne.core.utilities.CommonUtilities;
import com.ya0ne.core.utilities.DateUtilities;
import com.ya0ne.np.NMEAProcessor;

public class ReceiverStub implements Runnable {
	ArrayBlockingQueue<Object> queue;
	String inputFile;
	
	public ReceiverStub( ArrayBlockingQueue<Object> queue, String inputFile ) {
		this.queue = queue;
		this.inputFile = inputFile;
		try {
			NMEAProcessor.bulkUploadFile.set(DateUtilities.trackDateTime(CommonUtilities.parseFileName(inputFile)));
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		Path path = FileSystems.getDefault().getPath(this.inputFile);
		List<String> lines = null;
		try {
			lines = Files.readAllLines(path, Charset.defaultCharset());
		} catch (IOException e) {
			e.printStackTrace();
		}
		Iterator<String> iterator = lines.iterator();
		while( iterator.hasNext() ) {
			Object obj = iterator.next();
			if( obj instanceof String ) {
				try {
					queue.put((String) obj);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
