package io.openmessaging.demo;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * READ ONLY MappedByteBuffer Wrapper
 * for Consumer
 */
public class ReadBuffer {

	public String bucket = "";
	public FileChannel mappedFileChannel;
	public MappedByteBuffer buffer;
	public int size;
	public int offsetInFile;

	public ReadBuffer() {
		// do nothing
	}

	/**
	 * return false when no more file content to map. that is to say:
	 * mappedFileChannel.size() = offsetInFile
	 */
	public boolean reMap(String bucket, FileChannel fileChannel, int offset, int size) {
		try {
			if (fileChannel.size() - offset < size) {
				size = (int) (fileChannel.size() - offset);
			}
			if (size != 0) {
				buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, offset, this.size);
				this.bucket = bucket;
				mappedFileChannel = fileChannel;
				offsetInFile = offset + size;
				this.size = size;
				return true;
			}
		} catch (IOException e) {
			e.printStackTrace();
			this.bucket = "";
		}
		return false;
	}

	public boolean reMap() {
		return reMap(bucket, mappedFileChannel, offsetInFile, size);
	}

	public boolean reMap(int offset, int size) {
		return reMap(bucket, mappedFileChannel, offset, size);
	}

	/**
	 * 
	 * @param bucket bucket's name
	 * @param fileChannel the fileChannel of bucket
	 * @param offset 
	 * @param length
	 * @return null when no more new record
	 */
	public byte[] read(String bucket, FileChannel fileChannel, int offset, int length) {
		byte[] result = new byte[length];
		// readIndexFileBuffer 缓存不命中
		if (!bucket.equals(this.bucket)) {
			// 1. 不是同一个文件
			if (!reMap(bucket, fileChannel, offset, Constants.BUFFER_SIZE)) {
				return null; // no more to map == no more new record
			}
			// TODO 测试 load 与 不 load 谁快
			// buffer.load();
			// TODO 边界用 >= ? 待测
		} else if (offset >= offsetInFile) {
			// 2. 超出映射范围
			if (!reMap(offset, Constants.BUFFER_SIZE)) {
				return null; // no more to map == no more new record
			}
			// buffer.load();
		}

		// TODO 边界用 >= ? 待测
		if (length > offsetInFile - offset) {
			// 读两段
			int size1 = (int) (offsetInFile - offset);
			buffer.get(result, 0, size1);
			if (!reMap()) {
				return null; // ERROR 文件损坏？
			}
			buffer.get(result, size1, length - size1);
		} else {
			// other, 正常读
			if(buffer.remaining() < length)
				return null; // ERROR 文件损坏？
			buffer.get(result);
		}
		return result;
	}
}
