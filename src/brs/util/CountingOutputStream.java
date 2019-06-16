package brs.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class CountingOutputStream extends FilterOutputStream {

  private long count;

  public CountingOutputStream(OutputStream out) {
    super(out);
  }

  @Override
  public void write(int b) throws IOException {
    count += 1;
    super.write(b);
  }

  @Override
  public void write(byte[] b) throws IOException {
    count += b.length;
    super.write(b);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    count += len;
    super.write(b, off, len);
  }

  public long getCount() {
    return count;
  }
}
