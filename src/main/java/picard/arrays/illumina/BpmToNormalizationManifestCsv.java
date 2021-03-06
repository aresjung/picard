package picard.arrays.illumina;

import htsjdk.samtools.util.IOUtil;
import org.broadinstitute.barclay.argparser.Argument;
import org.broadinstitute.barclay.argparser.CommandLineProgramProperties;
import org.broadinstitute.barclay.help.DocumentedFeature;
import picard.PicardException;
import picard.cmdline.CommandLineProgram;
import picard.cmdline.StandardOptionDefinitions;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.StringJoiner;

/**
 * A simple program to convert an Illumina bpm (bead pool manifest file) into a normalization manifest (bpm.csv) file
 * The normalization manifest (bpm.csv) is a simple text file generated by Illumina tools - it has a specific format
 * and is used by ZCall .
 */
@CommandLineProgramProperties(
        summary = BpmToNormalizationManifestCsv.USAGE_DETAILS,
        oneLineSummary = "Program to convert an Illumina bpm file into a bpm.csv file.",
        programGroup = picard.cmdline.programgroups.GenotypingArraysProgramGroup.class
)
@DocumentedFeature
public class BpmToNormalizationManifestCsv extends CommandLineProgram {
    static final String USAGE_DETAILS =
            "BpmToNormalizationManifestCsv takes an Illumina BPM (Bead Pool Manifest) file and generates an Illumina-formatted bpm.csv file from it. " +
                    "A bpm.csv is a file that was generated by an old version of Illumina's Autocall software. " +
                    "Since it contained normalization IDs (needed to calculate normalized intensities), it came into use in several programs " +
                    "notably zCall (https://github.com/jigold/zCall)." +
                    "<h4>Usage example:</h4>" +
                    "<pre>" +
                    "java -jar picard.jar BpmToNormalizationManifestCsv \\<br />" +
                    "      INPUT=input.bpm \\<br />" +
                    "      CLUSTER_FILE=input.egt \\<br />" +
                    "      OUTPUT=output.bpm.csv" +
                    "</pre>";

    @Argument(shortName = StandardOptionDefinitions.INPUT_SHORT_NAME, doc = "The Illumina Bead Pool Manifest (.bpm) file")
    public File INPUT;

    @Argument(shortName = "CF", doc = "An Illumina cluster file (egt)")
    public File CLUSTER_FILE;

    @Argument(shortName = StandardOptionDefinitions.OUTPUT_SHORT_NAME, doc = "The output (bpm.csv) file to write.")
    public File OUTPUT;

    @Override
    protected int doWork() {
        IOUtil.assertFileIsReadable(INPUT);
        IOUtil.assertFileIsReadable(CLUSTER_FILE);
        IOUtil.assertFileIsWritable(OUTPUT);

        final InfiniumEGTFile infiniumEGTFile;
        try {
            infiniumEGTFile = new InfiniumEGTFile(CLUSTER_FILE);
        } catch (IOException e) {
            throw new PicardException("Error reading cluster file '" + CLUSTER_FILE.getAbsolutePath() + "'", e);
        }

        final IlluminaBPMFile illuminaBPMFile;
        try {
            illuminaBPMFile = new IlluminaBPMFile(INPUT);
        } catch (IOException e) {
            throw new PicardException("Error reading bpm file '" + INPUT.getAbsolutePath() + "'", e);
        }

        final DecimalFormat df = new DecimalFormat(("0.0000"));
        try (BufferedWriter writer = Files.newBufferedWriter(OUTPUT.toPath())) {
            writer.write("Index,Name,Chromosome,Position,GenTrain Score,SNP,ILMN Strand,Customer Strand,NormID");
            writer.newLine();
            for (IlluminaBPMLocusEntry locusEntry : illuminaBPMFile.getLocusEntries()) {
                StringJoiner joiner = new StringJoiner(",");
                float genTrainScore = infiniumEGTFile.totalScore[locusEntry.index];
                joiner.add("" + (locusEntry.index + 1));
                joiner.add(locusEntry.name);
                joiner.add(locusEntry.chrom);
                joiner.add("" + locusEntry.mapInfo);
                joiner.add(df.format(genTrainScore));
                joiner.add(locusEntry.snp);
                joiner.add(locusEntry.ilmnStrand);
                joiner.add(locusEntry.customerStrand);
                joiner.add("" + locusEntry.normalizationId);
                writer.write(joiner.toString());
                writer.newLine();
            }
        } catch (IOException e) {
            throw new PicardException("Error writing bpm.csv file '" + OUTPUT.getAbsolutePath() + "'", e);
        }
        return 0;
    }
}
