package io.openmessaging.demo;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class LastFile {
	private final String path;
	private final String fileName = Constants.LAST_FILE_NAME;
	private final RandomAccessFile lastFile;

	// 下面三个属性`互斥访问`以保证正确性
	private long nextIndexOffset;
	private long nextMessageOffset;
	private final byte[] lastIndexByte = new byte[Constants.INDEX_SIZE];

	private boolean close = false;

	public LastFile(String path) {
		this.path = path;
		File last = new File(path, fileName);
		try {
			if (!last.exists()) {
				last.createNewFile();
				lastFile = new RandomAccessFile(last, "rw");
				nextIndexOffset = 0;
				nextMessageOffset = 0;
			} else {
				lastFile = new RandomAccessFile(last, "rw");
				if (last.length() != Constants.LAST_FILE_SIZE) {
					nextIndexOffset = 0;
					nextMessageOffset = 0;
				} else {
					nextIndexOffset = lastFile.readLong();
					nextMessageOffset = lastFile.readLong();
					long offset = lastFile.readLong();
					int size = lastFile.readInt();
					Index.setOffset(lastIndexByte, offset);
					Index.setSize(lastIndexByte, size);
				}
			}
		} catch (IOException e) {
			throw new ClientOMSException("LastFile create failure", e);
		}
	}

	// 仅用于 topic 构造 及 ReadBuffer 判断最后一条 Index
	public long getNextIndexOffset() {
		return nextIndexOffset;
	}

	// 仅用于 topic 构造
	public long getNextMessageOffset() {
		return nextMessageOffset;
	}

	// for Producer, updateAndAppendIndex 要串行！！
	public synchronized long updateAndAppendIndex(int size, WriteBuffer3 writeIndexFileBuffer)
			throws InterruptedException {
		long newOffset = nextMessageOffset;
		Index.setOffset(lastIndexByte, newOffset);
		Index.setSize(lastIndexByte, size);
		GlobalResource.putWriteTask(new WriteTask(writeIndexFileBuffer, lastIndexByte.clone(), nextIndexOffset));
		nextIndexOffset += Constants.INDEX_SIZE;
		nextMessageOffset += size;
		if (close) {
			flush();
		}
		return newOffset;
	}

	// 仅在写文件时加个锁即可
	public synchronized void flush() {
		if (!close)
			close = true;
		try {
			lastFile.seek(0);
			lastFile.writeLong(nextIndexOffset);
			lastFile.writeLong(nextMessageOffset);
			lastFile.writeLong(Index.getOffset(lastIndexByte));
			lastFile.writeInt(Index.getSize(lastIndexByte));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
