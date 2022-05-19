/*
 * Copyright 2022 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.infrastructure.ssz.sos;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

public class SszOutputStreamWriter implements SszWriter {
  private final OutputStream out;

  public SszOutputStreamWriter(final OutputStream out) {
    this.out = out;
  }

  @Override
  public void write(final byte[] bytes, final int offset, final int length) {
    try {
      out.write(bytes, offset, length);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}