package featurecat.lizzie.analysis;

import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;

/** External GTP fixture: exercises actual process/pipe boundaries without a neural network. */
public final class FakePassGtp {
  public static void main(String[] args) throws Exception {
    PrintWriter log = new PrintWriter(new FileOutputStream(args[0], true), true);
    log.println("START");
    AtomicInteger generation = new AtomicInteger();
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    String line;
    while ((line = in.readLine()) != null) {
      log.println(line);
      String[] parts = line.split(" ", 2);
      int id = Integer.parseInt(parts[0]);
      if (parts[0].length() > 9) throw new AssertionError("KataGo GTP ID overflow");
      String cmd = parts[1];
      int token = generation.incrementAndGet();
      if (cmd.startsWith("kata-analyze") && args.length > 1 && args[1].equals("fail")) {
        System.out.println("?" + id + " analysis failed\n");
        System.out.flush();
        continue;
      }
      // Deliberately send a late result before the stop barrier's acknowledgement.
      if (cmd.equals("stop")) System.out.println("info move D4 visits 99999 winrate 0.9"
          + " scoreMean 999 scoreStdev 1 prior 0.1 order 0 pv D4");
      System.out.println("=" + id + "\n");
      System.out.flush();
      if (cmd.startsWith("kata-analyze")) {
        Thread t = new Thread(() -> {
          try {
            for (int visits : new int[] {250, 1000, 4000}) {
              Thread.sleep(150);
              if (generation.get() != token) return;
              if (args.length > 1 && args[1].equals("gated") && visits == 4000) {
                while (!new File(args[0]+".release").exists()) {
                  if (generation.get()!=token) return;
                  Thread.sleep(10);
                }
              }
              if (args.length > 1 && args[1].equals("mixed")) {
                System.out.println("info move D4 visits 7500 winrate 0.9 scoreMean 22.8"
                    + " scoreStdev 1 prior 0.1 order 0 pv D4"
                    + " info move C3 visits 2500 winrate 0.8 scoreMean 18"
                    + " scoreStdev 1 prior 0.1 order 1 pv C3"
                    + " info move Q16 visits 7500 winrate 0.9 scoreMean 22.8"
                    + " scoreStdev 1 prior 0.1 order 2 isSymmetryOf D4 pv Q16");
                System.out.flush();
                return;
              }
              System.out.println("info move D4 visits " + visits
                  + " winrate 0.9 scoreMean 6.3 scoreStdev 1 prior 0.1 order 0 pv D4");
              System.out.flush();
            }
          } catch (InterruptedException ignored) {}
        });
        t.setDaemon(true);
        t.start();
      }
    }
  }
}
