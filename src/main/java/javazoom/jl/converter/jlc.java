/*
 * 11/19/04        1.0 moved to LGPL.
 *
 * 29/01/00        Initial version. mdm@techie.com
 *
 * 12/12/99     JavaLayer 0.0.7 mdm@techie.com
 *
 * 14/02/99     MPEG_Args Based Class - E.B
 * Adapted from javalayer and MPEG_Args.
 * Doc'ed and integrated with JL converter. Removed
 * Win32 specifics from original Maplay code.
 *-----------------------------------------------------------------------
 *   This program is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU Library General Public License as published
 *   by the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Library General Public License for more details.
 *
 *   You should have received a copy of the GNU Library General Public
 *   License along with this program; if not, write to the Free Software
 *   Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *----------------------------------------------------------------------
 */

package javazoom.jl.converter;

import java.io.PrintWriter;
import java.util.logging.Logger;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.OutputChannels;


/**
 * The <code>jlc</code> class presents the JavaLayer
 * Conversion functionality as a command-line program.
 *
 * @since 0.0.7
 */
public class jlc {

    private static final Logger logger = Logger.getLogger(jlc.class.getName());

    static public void main(String[] args) {
        String[] argv;
//        long start = System.currentTimeMillis();
        int argc = args.length + 1;
        argv = new String[argc];
        argv[0] = "jlc";
        System.arraycopy(args, 0, argv, 1, args.length);

        jlcArgs ma = new jlcArgs();
        if (!ma.processArgs(argv))
            System.exit(1);

        Converter conv = new Converter();

        int detail = (ma.verboseMode ? ma.verboseLevel : Converter.PrintWriterProgressListener.NO_DETAIL);

        Converter.ProgressListener listener = new Converter.PrintWriterProgressListener(new PrintWriter(System.out, true),
                detail);

        try {
            conv.convert(ma.filename, ma.outputFilename, listener);
        } catch (JavaLayerException ex) {
            logger.warning("Conversion failure: " + ex);
        }

        System.exit(0);
    }

    /**
     * Class to contain arguments for maplay.
     */
    static class jlcArgs {
        // channel constants moved into OutputChannels class.

        public int whichC;

        public int outputMode;

        public boolean useOwnScalefactor;

        public float scaleFactor;

        public String outputFilename;

        public String filename;

        public boolean verboseMode;

        public int verboseLevel = 3;

        public jlcArgs() {
            whichC = OutputChannels.BOTH_CHANNELS;
            useOwnScalefactor = false;
            scaleFactor = (float) 32768.0;
            verboseMode = false;
        }

        /**
         * Process user arguments.
         * <p>
         * Returns true if successful.
         */
        public boolean processArgs(String[] argv) {
            filename = null;
//            Crc16[] crc = new Crc16[1];
            int i;
            int argc = argv.length;

            verboseMode = false;
            outputMode = OutputChannels.BOTH_CHANNELS;
            outputFilename = "";
            if (argc < 2 || argv[1].equals("-h"))
                return usage();

            i = 1;
            while (i < argc) {
logger.finer("Option = " + argv[i]);
                if (argv[i].charAt(0) == '-') {
                    if (argv[i].startsWith("-v")) {
                        verboseMode = true;
                        if (argv[i].length() > 2) {
                            try {
                                String level = argv[i].substring(2);
                                verboseLevel = Integer.parseInt(level);
                            } catch (NumberFormatException ex) {
                                System.err.println("Invalid verbose level. Using default.");
                            }
                        }
                        System.out.println("Verbose Activated (level " + verboseLevel + ")");
                    } else if (argv[i].equals("-p")) {
                        if (++i == argc) {
                            System.out.println("Please specify an output filename after the -p option!");
                            System.exit(1);
                        }
                        outputFilename = argv[i];
                    } else
                        return usage();
                } else {
                    filename = argv[i];
                    System.out.println("FileName = " + argv[i]);
                    if (filename == null)
                        return usage();
                }
                i++;
            }
            if (filename == null)
                return usage();

            return true;
        }

        /**
         * Usage of JavaLayer.
         */
        public boolean usage() {
            System.out.println("JavaLayer Converter :");
            System.out.println("  -v[x]         verbose mode. ");
            System.out.println("                default = 2");
//            System.out.println("  -s         write u-law samples at 8 kHz rate to stdout");
//            System.out.println("  -l         decode only the left channel");
//            System.out.println("  -r         decode only the right channel");
//            System.out.println("  -d         downmix mode (layer III only)");
//            System.out.println("  -s         write pcm samples to stdout");
//            System.out.println("  -d         downmix mode (layer III only)");
            System.out.println("  -p name    output as a PCM wave file");
            System.out.println();
            System.out.println("  More info on http://www.javazoom.net");
//            System.out.println("  -f ushort  use this scalefactor instead of the default value 32768");
            return false;
        }
    }
}
