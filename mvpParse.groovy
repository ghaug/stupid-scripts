#!/usr/bin/env groovy

import java.text.SimpleDateFormat
import java.time.OffsetTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.Duration
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO
import java.awt.*
import java.awt.image.BufferedImage

/**
 * @author gunter
 *
 */
class MvpParse {

  void giveHelp() {
    println 'mvpParse [options] file'
    println '    options: '
    println '           -d      dump'
    println '           -f      print fuel used'
    println '           -h      show this and exit'
    println '           -i      output flight information'
    println '           -k out  write kml/kmz file'
    println '           -a      track direction arrows (will generate kmz)'
    println '           -b int  icon/arrow size, with -e affects both, without only arrows (0 to 9, default 4)'
    println '           -c int  color scheme in kml (0: mono, 1: color full, 2: journeys)'
    println '           -e      optimize kml for Google Earth'
    println '           -j      icons in kml'
    println '           -m      mix up colors in kml'
    println '           -o      omit local flights in kml'
    println '           -r      output rich kml (for interactive use of GE)'
    println '           -u int  position update interval in kml (default 2)'
    println '           -w int  line width in kml (default 5)'
    println '           -s      output summary of internal data'
    println '           -t name output flight information in latex format'
    println '           -v      output debug info on stdout'
    println '    file [file...]:'
    println '             csv file(s)'
  }

  // here we go: run
  def run(args) {
    try {
      // first of all parse command line arguments
      def optFound = false
      def kml = false
      def dump = false
      def summary = false
      def fuelUsed = false
      def information = false
      def exit = false
      def kmlFileName = ''
      def kmlDirArrows = false
      def kmlDirArrowSize = 4
      def csvFileNames = []
      def ignore = 0
      def tex = 0
      def texFileName = ''
      def numFiles = 0
      def kmlStep = 2
      def kmlWidth = 5
      def kmlForGE = false
      def kmlIcons = false
      def richKml = false
      def kmlColorScheme = 0
      def kmlOmitLocals = false
      def mixColors = false
      def deck = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]

      args.eachWithIndex { arg, i ->
        if (ignore == 0) {
          switch (arg) {
            case'-s' :
              summary = true
              optFound = true
              break
            case '-i' :
              information = true
              optFound = true
              break
            case '-d' :
              dump = true
              optFound = true
              break
            case '-f' :
              fuelUsed = true
              optFound = true
              break
            case '-k' :
              kml = true
              optFound = true
              if (args.length > i + 1) {
                kmlFileName = args[i + 1]
              }
              ignore = 1
              break
            case '-t' :
              tex = true
              optFound = true
              if (args.length > i + 1) {
                texFileName = args[i + 1]
              }
              ignore = 1
              break
            case '-v' :
              if (args.length > i + 1) {
                Globals.debug = args[i + 1] as int
              }
              ignore = 1
              break
            case '-u' :
              if (args.length > i + 1) {
                kmlStep = args[i + 1] as int
              }
              ignore = 1
              break
            case '-r' :
              richKml = true
              break
            case '-a' :
               kmlDirArrows= true
              break
            case '-j' :
              kmlIcons = true
              break
            case '-m' :
              mixColors = true
              break
            case '-e' :
              kmlForGE = true
              break
            case '-w' :
              if (args.length > i + 1) {
                kmlWidth = args[i + 1] as int
              }
              ignore = 1
              break
            case '-b' :
              if (args.length > i + 1) {
                kmlDirArrowSize = args[i + 1] as int
              }
              ignore = 1
              break
            case '-c' :
              if (args.length > i + 1) {
                kmlColorScheme = args[i + 1] as int
              }
              ignore = 1
              break
            case '-o' :
              kmlOmitLocals = true
              break
            case { arg.substring(0, 1) != '-' } :
              csvFileNames[numFiles++] = arg
              break
            case '-h' :
              giveHelp()
              exit = true
              return
            default:
              throw new WrongArgException("Don't understand " + arg + ', try -h')
          }
        } else {
          ignore--
        }
      }
      if (exit) return

      def rnd = new Random()
      if (mixColors) {
        for (def j = 0; j< 16; ++j) {
          def r = rnd.nextInt(16)
          def t = deck[j]
          deck[j] = deck[r]
          deck[r] = t
        }
      }

      if (!optFound) summary = true

      if (numFiles == 0) throw new WrongArgException('No files provided')

      def kmlPrintStream = null
      def kmzDir = null
      def kmlFile = null
      if (kml) {
        if (kmlFileName == '') throw new WrongArgException('No kml file provided')
        if (kmlDirArrows) {
          def proc = "mktemp -d".execute()
          proc.waitFor()
          kmzDir = proc.in.text
          kmzDir = kmzDir.substring(0, kmzDir.length() - 1)
          kmlFile = new File("${kmzDir}/doc.kml")
          proc = "mkdir ${kmzDir}/images".execute()
          proc.waitFor()
          if (kmlForGE) {
            BufferedImage bi = new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB)
            for (def x = 0; x < 32; x++) for (def y = 0; y < 16; y++) bi.setRGB(x, y, 0)
            int color = 0xFFFFFFFF
            for (def j = 0; j < ((kmlWidth << 2) + kmlWidth) >> 1; j++) {
              for (def i = 0; i < (16 - j); i++) {
                bi.setRGB(16+i, i + j, color)
                bi.setRGB(16-i, i + j, color)
              }
            }
            ImageIO.write(bi, "png", new File("${kmzDir}/images/arrow.png"))
            def adIconString = "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAABhGlDQ1BJQ0MgcHJvZmlsZQAAKJF9kT1Iw0AcxV9bRSmtDlYRcchQneyiIo61CkWoEGqFVh1MLv2CJg1Jiouj4Fpw8GOx6uDirKuDqyAIfoA4OzgpukiJ/0sKLWI8OO7Hu3uPu3eAv1FhqtkVB1TNMtLJhJDNrQo9rwgijD4MYVBipj4niil4jq97+Ph6F+NZ3uf+HGElbzLAJxDHmW5YxBvEM5uWznmfOMJKkkJ8Tjxh0AWJH7kuu/zGueiwn2dGjEx6njhCLBQ7WO5gVjJU4mniqKJqlO/Puqxw3uKsVmqsdU/+wlBeW1nmOs1RJLGIJYgQIKOGMiqwEKNVI8VEmvYTHv4Rxy+SSyZXGYwcC6hCheT4wf/gd7dmYWrSTQolgO4X2/4YA3p2gWbdtr+Pbbt5AgSegSut7a82gNlP0uttLXoE9G8DF9dtTd4DLneA4SddMiRHCtD0FwrA+xl9Uw4YuAWCa25vrX2cPgAZ6ip1AxwcAuNFyl73eHdvZ2//nmn19wNlvXKho6NyOQAAAAZiS0dEAMwAzADM38FfGgAAAAlwSFlzAAALEwAACxMBAJqcGAAAAAd0SU1FB+gKEww1IK6ayIUAAAAZdEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIEdJTVBXgQ4XAAAE7klEQVR42u2bT2hUVxTGf0cCwdSFYCEJtEgRF/VfxDZUIbaLQmyEJBVaqS1RELJIzEZCiBCCiM0ihJJNzCykCw3FQrOwU1B0VwzYEpFEQ7ooLkRxFBRcWCUgni7OHTJ9Jcl7M29e3p14Npf5d9/3fXPuveecd56QkImIRvm+qkoSuNaxxq0q6QtOT08v+3ljY2OieNa8B7wVIKENcG8Sv0mVACLSKiI/isgj4GYRU9wUkUdujlYvBBCRDSJyUkRmgCxwHKiHjiJm68B+y3EgKyIzbu4NsWKOkXwX0ANsWyTQjOqHQC0i70c6BVQfAE8Q+Qu4DkzkvzIPjKlqJhXHoIg0AX1Am71zEtVmYHsM+taiWgt8BnyLyHVgdBswLiJfACOqOrVqAojICWDAXPUrVL8DPorTsQocdQeq24FPEfkJmGwDGkVkSFXPJSqAiLwDDAL99s4PqH4ObCr3eQJ8jOoHiOwDeuuBMbH1dVZV/yn7JigitcCIkX8PuITq1wmQL7RN7pqXMAz0AyMOW/kEcP/8aaALDqKaQbWpDC4fzhtUm7C98CCGidMOY9k8YNAudADVE8DuFMRyux2WA3kRBssigNvw+u2I6gZ2pSig3eUwAdDvsMYngDvqBuzVBLAnhVH9nsJYYcBhjs0D+uyoG3JrPp1m2IbyEWRfLAK4CK8N9rsAp4r0WpXDuB+gzWEvXgAXd/fYq26gzoMEt85hBaBnpdxhJQ/otNj+KKqN+GKG9SguL+ksRYBjNmE7UO1RmaPaYV7kEFkAl4M3WCKy08Naz06XRNGwXD1hOQ/40oZvgPUeCrDeYS/kEk2AFnP/HfhqBdhbIgng6nH15kKb8dc255dB/VI1xqU84BMbmlcp0YkzfW4OcAonQIMNW/DftgQ4hRNgq62hOu/pF3DYGkUAq2CysQI8YGOAUzgB3rWhpgIEqAlwCidATT6i8t+q/6dEhFC4Em4drivq05c2vKkAAd4EOIUT4KkNCxUgwEKAUzgBHiwjmmf2MsApnAB/2/C8AgR4HuAUToBZywkee0+/gMNsFAH+tOFeBXjAvQCnEAKo6h9Azm5Lq8fkFeNAznGKdEhehd+B+x4LcB/jwNViooTLtobmPF7/c//hEkkAVf3NNo6fgVce0n+FYWfWcSkq1r1gLnTXQwHu5t3/QinB/nlgXuRXz6LCBQwz845DcQKo6gtgDC4iMu3R2p8GLoI1U70oKd1z3VhZGAd8CIweY1jJhukkC5vvjsCNnHVpvU4x+deuk+xGzjCXmiwvesEUMAQDiEyl2PWncG0MQ2Hb50JXPFwr2rA1QN5OIf3buO7S4Shtc1FLPmeBjMg4cCdF5O9gmMg4jJRFANeHdwauZUTOATMpID+DYbmWAc5E7RWMXPRT1SdAH1wZFuly6241EiZFZAprArkyDPQ5bJRVgLwnqOopeNgDR3IivwDPEiT/DLvmkRw87FHVU8V0iRYtQGBjPAy9WZHvgVtl9gYFbmHX6s0Ch0vpEy5ZgPwRqartMNktcmheZBSYi1kIBeYQGUXk0DxMdqtqe6md4hDzrV/XkNQJHIOOhpifF5h1ic35lcLbVRMgIEYr1pnRAh31+SbG8I/NdQATOVfMuLxcSptKAQJi7MU9NxThucF9S5Wx4rRE7n0VQyQJ8okJkGZb8wIk1gD09unxlNq/nsek+qex24YAAAAASUVORK5CYII="
            def adIconBytes = Base64.getDecoder().decode(adIconString)
            OutputStream stream = new FileOutputStream("${kmzDir}/images/ad.png")
            stream.write(adIconBytes)
          }
        } else {
          kmlFile = new File(kmlFileName)
        }
        if (kmlFile == null) throw new FileException('Cannot write ' + kmlFileName)
        kmlPrintStream = new PrintStream(kmlFile)
        if (kmlPrintStream == null)  throw new FileException('Cannot open ' + kmlFileName)
      }

      def texPrintStream = null
      if (tex) {
        if (texFileName == '') throw new WrongArgException('No name provided')
        def texFile = new File(texFileName + '.tex')
        if (texFile == null) throw new FileException('Cannot write ' + texFileName + '.tex')
        texPrintStream = new PrintStream(texFile)
        if (texPrintStream == null)  throw new FileException('Cannot open ' + texFileName)
      }

      def journey = 0
      def fltTime = Duration.ZERO
      def blkTime = Duration.ZERO
      def track = 0
      def fuel = 0.0
      float tachStart
      float tachEnd
      for (def i = 0; i < numFiles; i++) {
        def csvFile = new File(csvFileNames[i])
        if (csvFile == null || !csvFile.exists() || !csvFile.canRead()) throw new FileException('Cannot find ' + csvFileName)

        def flight = new Flight(csvFile)

        if (numFiles > 1 && i == 0) tachStart = flight.tachStart
        if (numFiles > 1 && i == (numFiles - 1)) tachEnd = flight.tachEnd
        if (flight.flightDuration) fltTime = fltTime.plus(flight.flightDuration)
        if (flight.blockDuration) blkTime = blkTime.plus(flight.blockDuration)
        track += flight.track
        fuel += flight.integFuel

        if (summary) flight.printSummary()
        if (information) {
          flight.printInformation()
          if (i < (numFiles -1)) println "-------------------"
        }
        if (dump) flight.dump()
        if (kml) {
          def colorIndex = 16
          if (kmlColorScheme == 1) colorIndex = i % 16
          if (kmlColorScheme == 2) colorIndex = journey % 16
          colorIndex = deck[colorIndex]
          if (kmlColorScheme == 0 && mixColors) colorIndex = deck[0]
          if (!kmlOmitLocals || !(flight.startsAtHome && flight.endsAtHome) && flight.flightDuration != null && flight.flightDuration.getSeconds() > 300 ) {
            def f2 = new Flight(flight, kmlStep)
            f2.printTrack(kmlPrintStream, i == 0, i == (numFiles - 1), colorIndex, kmlWidth, richKml, kmlIcons, kmlForGE, kmlDirArrows, kmzDir, i, kmlDirArrowSize)
            if (flight.endsAtHome && !mixColors) journey++
            if (flight.endsAtHome && mixColors) journey += (rnd.nextInt(5) + 1)
          }
        }
        if (tex) {
          flight.printTex(texPrintStream, texFileName)
        }
        if (fuelUsed) println flight.integFuel
      }
      if (information && numFiles > 1) {
        println "-------------------"
        println "Accumulated:"
        def h = fltTime.toHours()
        def minD = fltTime.minusHours(h)
        def m = minD.toMinutes()
        println "FltTime: ${h}:${String.format('%02d', new Long(m))}"
        h = blkTime.toHours()
        minD = blkTime.minusHours(h)
        m = minD.toMinutes()
        println "BlkTime: ${h}:${String.format('%02d', new Long(m))}"
        println "Track: ${track}NM"
        println "Fuel: ${String.format(Locale.US, '%.1f', new Double(fuel))}USG"
        println "Tach Time: ${String.format(Locale.US, '%.2f', new Float(tachEnd - tachStart))}"
      }
      if (kml && kmlDirArrows) {
        def proc = "zip -r ${kmlFileName} doc.kml images".execute(null, new File("${kmzDir}"))
        proc.waitFor()
        proc = "mv ${kmzDir}/${kmlFileName} .".execute()
        proc.waitFor()
        proc = "rm -rf ${kmzDir}".execute()
        proc.waitFor()
      }
    }
    catch (WrongArgException e) {
      println 'Wrong command line argument! ' + e.text()
    }
    catch (FileException e) {
      println e.text()
    }
    catch (Throwable t) {
      throw t
    }
}
  // main, not much to do here
  static main(args) {
    def mvpParse = new MvpParse()
    mvpParse.run(args)
  }

}

class Globals {
  static debug = 0
}

class WrongArgException
extends Exception {
  String txt
  WrongArgException(String s) { super(s); txt = s }
  String text() { return txt }
}

class FileException
extends Exception {
  String txt
  FileException(String s) { super(s); txt = s  }
  String text() { return txt }
}

class Flight {

  def fltNum
  def fltDateFormat
  def fltStart
  float tachStart
  float tachEnd
  def loggingDuration
  def data = []
  def flightDuration = null
  def blockDuration = null
  def takeOffTime
  def takeOffLat
  def takeOffLong
  def takeOffAlt
  def landingTime
  def landingLat
  def landingLong
  def landingAlt
  def fuelDiff
  def offBlock
  def offBlockLat
  def offBlockLong
  def offBlockAlt
  def onBlock
  def onBlockLat
  def onBlockLong
  def onBlockAlt
  double integFuel
  double avgSpeed
  double dist
  long track
  def maxAlt
  def labels
  double homeLat = 49.473
  double homeLong = 8.51323
  def startsAtHome
  def endsAtHome
  def lastFltIndex

  def distance(lat1, long1, lat2, long2) {
    double phi1 = Math.toRadians(lat1)
    double phi2 = Math.toRadians(lat2)
    double mPhi = Math.toRadians((lat1 + lat2) / 2)
    double dPhi = Math.toRadians(lat2 - lat1)
    double dLambda = Math.toRadians(long2 - long1)
    double R = 3443.84449 - 7.01493844 * Math.sin(mPhi)
    double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2) + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2)
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return R * c
  }

  def coordTrack(lat1, long1, lat2, long2) {
    double phi1 = Math.toRadians(lat1)
    double phi2 = Math.toRadians(lat2)
    double dLambda = Math.toRadians(long2 - long1)
    double y = Math.sin(dLambda) * Math.cos(phi2)
    double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLambda)
    double theta = Math.atan2(y, x)
    return (theta * 180/Math.PI + 360) % 360
  }

  def printSummary() {
    println "Flight Number: ${fltNum}"
    if (blockDuration != null) {
      println "OFB: ${offBlock}"
    }
    if (flightDuration != null) {
      println "T/O: ${takeOffTime}"
      println "LDG: ${landingTime}"
    }
    if (blockDuration != null) {
      println "ONB: ${onBlock}"
    }
    if (flightDuration != null) {
      println "Flight Time: ${flightDuration}"
    }
    if (blockDuration != null) {
      println "Block Time: ${blockDuration}"
    }
    println "Logging Time: ${loggingDuration}"
    println "Fuel diff: ${fuelDiff} ${labels.est_fuelUnit}"
    println "Fuel used: ${integFuel} ${labels.est_fuelUnit}"
  }

  def printInformation() {
    println "Flight Number: ${fltNum}"
    if (offBlock != null && takeOffTime != null && landingTime != null && onBlock != null && flightDuration != null && blockDuration != null) {
      def ofb = offBlock.atOffset(ZoneOffset.UTC)
      def tof = takeOffTime.atOffset(ZoneOffset.UTC)
      def ldg = landingTime.atOffset(ZoneOffset.UTC)
      def onb = onBlock.atOffset(ZoneOffset.UTC)

      println "Date: ${ofb.format(DateTimeFormatter.ISO_LOCAL_DATE)}"

      println "OFB: ${ofb.format(DateTimeFormatter.ISO_LOCAL_TIME)}"
      println "T/O: ${tof.format(DateTimeFormatter.ISO_LOCAL_TIME)}"
      println "LDG: ${ldg.format(DateTimeFormatter.ISO_LOCAL_TIME)}"
      println "ONB: ${onb.format(DateTimeFormatter.ISO_LOCAL_TIME)}"
      def h = flightDuration.toHours()
      def minD = flightDuration.minusHours(h)
      def m = minD.toMinutes()
      println "FltTime: ${h}:${String.format('%02d', new Long(m))}"
      h = blockDuration.toHours()
      minD = blockDuration.minusHours(h)
      m = minD.toMinutes()
      println "BlkTime: ${h}:${String.format('%02d', new Long(m))}"

      println "Dist: ${Math.round(dist)}NM"
      println "Track: ${track}NM"
      println "maxAlt: ${maxAlt}ft"
      println "Fuel: ${String.format(Locale.US, '%.1f', new Double(integFuel))}USG"
      println "Tach Start: ${String.format('%.2f', new Float(tachStart))}"
      println "Tach End: ${String.format('%.2f', new Float(tachEnd))}"
    }
  }

  def printTex(file, name) {
    if (offBlock != null && takeOffTime != null && landingTime != null && onBlock != null && flightDuration != null && blockDuration != null) {
      file.println "\\documentclass[preview]{standalone}"
      file.println "\\usepackage{avant}"
      file.println "\\begin{document}"
      file.println "\\begin{minipage}[t]{3.5cm}"
      file.println "\\textbf{\\textsf{"
      file.println "\\centerline{\\Large ${name}}"

      def ofb = offBlock.atOffset(ZoneOffset.UTC)
      def tof = takeOffTime.atOffset(ZoneOffset.UTC)
      def ldg = landingTime.atOffset(ZoneOffset.UTC)
      def onb = onBlock.atOffset(ZoneOffset.UTC)

      file.println "\\centerline{${tof.format(DateTimeFormatter.ISO_LOCAL_DATE)}}"

      file.println "OFB:\\hfill ${ofb.format(DateTimeFormatter.ISO_LOCAL_TIME)}\\\\"
      file.println "T/O:\\hfill ${tof.format(DateTimeFormatter.ISO_LOCAL_TIME)}\\\\"
      file.println "LDG:\\hfill ${ldg.format(DateTimeFormatter.ISO_LOCAL_TIME)}\\\\"
      file.println "ONB:\\hfill ${onb.format(DateTimeFormatter.ISO_LOCAL_TIME)}\\\\"
      def h = flightDuration.toHours()
      def minD = flightDuration.minusHours(h)
      def m = minD.toMinutes()
      file.println "FltTime:\\hfill ${h}:${String.format('%02d', new Long(m))}\\\\"
      h = blockDuration.toHours()
      minD = blockDuration.minusHours(h)
      m = minD.toMinutes()
      file.println "BlkTime:\\hfill ${h}:${String.format('%02d', new Long(m))}\\\\"

      file.println "Dist:\\hfill ${Math.round(dist)}NM\\\\"
      file.println "Track:\\hfill ${track}NM\\\\"
      file.println "maxAlt:\\hfill ${maxAlt}ft\\\\"
      file.println "Fuel:\\hfill ${String.format(Locale.US, '%.1f', new Double(integFuel))}USG}}"
      file.println "\\end{minipage}"
      file.println "\\end{document}"
      println "pdflatex ${name}.tex ; convert -density 400 ${name}.pdf -quality 90 ${name}.png"
    }
  }

  def Flight (Flight f, int stepSeconds) {
    fltNum = f.fltNum
    fltDateFormat = f.fltDateFormat
    fltStart = f.fltStart
    tachStart = f.tachStart
    tachEnd = f.tachEnd
    loggingDuration = f.loggingDuration
    flightDuration = f.flightDuration
    blockDuration = f.blockDuration
    takeOffTime = f.takeOffTime
    takeOffLat = f.takeOffLat
    takeOffLong = f.takeOffLong
    takeOffAlt = f.takeOffAlt
    landingTime = f.landingTime
    landingLat = f.landingLat
    landingLong = f.landingLong
    landingAlt = f.landingAlt
    fuelDiff = f.fuelDiff
    offBlock = f.offBlock
    offBlockLat = f.offBlockLat
    offBlockLong = f.offBlockLong
    offBlockAlt = f.offBlockAlt
    onBlock = f.onBlock
    onBlockLat = f.onBlockLat
    onBlockLong = f.onBlockLong
    onBlockAlt = f.onBlockAlt
    integFuel = f.integFuel
    avgSpeed = f.avgSpeed
    dist = f.dist
    startsAtHome = f.startsAtHome
    endsAtHome = f.endsAtHome
    track = f.track
    maxAlt = f.maxAlt
    labels = f.labels

    def i
    for (i = 0; i < f.data.size() && (f.data[i].gps_lat == Double.NaN || f.data[i].gps_long == Double.NaN); i++);
    if (i == f.data.size()) {
      data = f.data
      return
    }
    def j
    for (j = f.data.size() - 1; j >= 0 && (f.data[j].gps_lat == Double.NaN || f.data[j].gps_long == Double.NaN); j--);

    data = []
    data << f.data[i]
    def last = f.data[i].timeStamp
    for (int k = i; k < j ; k++) {
      if (Duration.between(last, f.data[k].timeStamp).getSeconds() >= stepSeconds) {
        data << f.data[k]
        last = f.data[k].timeStamp
      }
    }
    lastFltIndex = -1
    for (i = data.size() - 1; i >= 0 && lastFltIndex == -1; --i) {
        if (data[i].flt_tm != 0.0 && data[i].gps_long != Double.NaN && data[i].gps_lat != Double.NaN) lastFltIndex = i
      }
  }

  def Flight (File f) {
    def rawDate
    def rawTime
    def date
    def nextDate
    def rawZulu
    def zuluOffset
    def startTime
    def lines = 0

    def inBody = false
    f.eachLine() { line ->
      if (!inBody && line =~ /^\d\d:\d\d:\d\d,/) {
        // First line of the body, lets process the data read in the header
        inBody = true


        // Get the start time, note that time in header may be after first logging time which is the start time
        def headerTimeA = rawTime.split(/:/)
        def headerTime = (headerTimeA[0] as int) * 3600 + (headerTimeA[1] as int) * 60 + (headerTimeA[2] as int)
        def rawFirstTime = line.split(/,/)[0]
        def firstTimeA = rawFirstTime.split(/:/)
        def firstTime = (firstTimeA[0] as int) * 3600 + (firstTimeA[1] as int) * 60 + (firstTimeA[2] as int)
        startTime = OffsetTime.parse(rawFirstTime + '+00:00')

        // Calculate Zulu offset
        def zuluTimeA = rawZulu.split(/:/)
        def zuluTime = (zuluTimeA[0] as int) * 3600 + (zuluTimeA[1] as int) * 60 + (zuluTimeA[2] as int)
        def zoneDiff = headerTime - zuluTime
        if (zoneDiff < -(16 * 3600)) {
          zoneDiff += (24 * 3600)
        } else if (zoneDiff > (16 * 3600)) {
          zoneDiff -= (24 * 3600)
        }
        def zoneDiffSign = zoneDiff < 0 ? '-' : '+'
        int zoneDiffMinutes = Math.round(Math.abs((zoneDiff as float) / 60.0f))
        int zoneDiffHours = zoneDiffMinutes / 60
        zoneDiffMinutes = zoneDiffMinutes - 60 * zoneDiffHours
        def min_str = zoneDiffMinutes < 10 ? '0' : ''
        min_str = min_str + Integer.toString(zoneDiffMinutes)
        def hr_str = zoneDiffHours < 10 ? '0' : ''
        hr_str = hr_str + Integer.toString(zoneDiffHours)
        zuluOffset = zoneDiffSign + hr_str + ':' + min_str

        // Determin date of start time
        if (this.fltDateFormat =~ "mm/dd/yy") {
          def month = rawDate.substring(0, 2)
          def day = rawDate.substring(3, 5)
          def year = rawDate.substring(6, 8)
          rawDate = "20${year}/${month}/${day}"
        }
        def fm = new SimpleDateFormat('yyyy/MM/dd')
        def d = fm.parse(rawDate)
        if ((firstTime - headerTime) > 23 * 3600) {
          def prev = new Date()
          prev.setTime(d.getTime() - (24 * 3600 * 1000))
          fm = new SimpleDateFormat('yyyy-MM-dd')
          date = fm.format(prev)
          nextDate = fm.format(d)
        }
        else {
          def next = new Date()
          next.setTime(d.getTime() + (24 * 3600 * 1000))
          fm = new SimpleDateFormat('yyyy-MM-dd')
          date = fm.format(d)
          nextDate = fm.format(next)
        }
        fltStart = OffsetDateTime.parse(date + 'T' + rawFirstTime + zuluOffset).toInstant()
      }
      if (!inBody) {
        // In header, collect data
        def m = line =~ /^Local Time: /
        if (m) {
          //println "line: ${line}"
          m = line =~ /\d\d:\d\d:\d\d/
          assert m
          rawTime = m.group()
          m = line =~ /\d\d\d\d\/\d\d\/[\d| ]\d/
          if (!m) m = line =~ /\d\d\/\d\d\/\d\d/
          assert m
          rawDate = m.group()
          rawDate = rawDate.replaceAll(' ', '0')
        }
        m = line =~ /^Date Format: /
        if (m) {
          m = line =~ /....\/..\/../
          assert m
          this.fltDateFormat = m.group()
        }
        m = line =~ /^Flight Number: /
        if (m) {
          m = line =~ /\d*$/
          assert m
          this.fltNum = m.group()
        }
        m = line =~ /^ZULU Time: /
        if (m) {
          m = line =~ /\d\d:\d\d:\d\d/
          assert m
          rawZulu = m.group()
        }
        m = line =~ /^TIME,/
        if (m) {
          labels = new Labels(line)
        }
      } else {
        // In body
        def values = []
        values = line.split(/,/)
        def lineTime = OffsetTime.parse(values[0] + '+00:00')
        def dateToUse = date
        if (lineTime.isBefore(startTime)) {
          dateToUse = nextDate
        }
        def timeStamp = OffsetDateTime.parse(dateToUse + 'T' + values[0] + zuluOffset).toInstant()
        def timeStampSet
        try {
          timeStampSet = new TimeStampedSet(timeStamp, values, labels)
        }
        catch (java.lang.NumberFormatException e) {
          timeStampSet = null
        }
        if (timeStampSet != null) {
          data << timeStampSet
          lines++
        }
      }
    }
    data.trimToSize()

    def firstWithGpsTime = null
    def firstLat = -1000.0
    def firstLong = -1000.0
    offBlock = null
    def ofbIndex = -1
    def onbIndex = -1
    data.eachWithIndex { set, i ->
      if (set.gps_waypt == 'GARMN') {
        set.gps_lat = Double.NaN
        set.gps_long = Double.NaN
      }
      if (firstWithGpsTime == null && set.gps_lat != Double.NaN && set.gps_long != Double.NaN) {
        firstWithGpsTime = set.timeStamp
      } else if (firstWithGpsTime != null && Duration.between(firstWithGpsTime, set.timeStamp).getSeconds() > 60 &&
        firstLat == -1000.0 && set.gps_lat != Double.NaN && set.gps_long != Double.NaN) {
        firstLat = set.gps_lat
        firstLong = set.gps_long
      } else if (offBlock == null && firstLat != -1000.0 && set.gps_lat != Double.NaN && set.gps_long != Double.NaN &&
                 distance(set.gps_lat, set.gps_long, firstLat, firstLong) > 0.010799) {
        offBlock = set.timeStamp
        offBlockLat = set.gps_lat
        offBlockLong = set.gps_long
        offBlockAlt = set.gps_alt
        ofbIndex = i
      }
    }
    tachStart = data[0].tach_tm
    tachEnd = data[data.size() - 1].tach_tm
    onBlock = null
    def lastLat = -1000.0
    def lastLong = -1000.0
    for (def i = data.size() - 1 ; i >= 0 ; i--) {
      if (lastLong == -1000.0 && data[i].gps_lat != Double.NaN && data[i].gps_long != Double.NaN) {
        lastLong = data[i].gps_long
        lastLat = data[i].gps_lat
      } else if (onBlock == null && lastLong != -1000.0 && data[i].gps_lat != Double.NaN && data[i].gps_long != Double.NaN &&
                        distance(data[i].gps_lat, data[i].gps_long, lastLat, lastLong) > 0.010799) {
        onBlock = data[i].timeStamp
        onBlockLat = data[i].gps_lat
        onBlockLong = data[i].gps_long
        onBlockAlt = data[i].gps_alt
        onbIndex = i;
      }
    }
    if (onBlock != null && offBlock != null) {
      blockDuration = Duration.between(offBlock, onBlock)
      fuelDiff = data[ofbIndex].est_fuel - data[onbIndex].est_fuel
    }
    def data2 = []
    for (int i = 0 ; i < lines ;) {
      int j
      for (j = i + 1; j < lines && data[i].timeStamp == data[j].timeStamp; ++j) ;
      def oneSec = []
      for (int k = i ; k < j; k++) oneSec << data[k]
      data2 << new TimeStampedSet(oneSec)
      i = j
    }
    data = data2

    def firstFltIndex = -1
    lastFltIndex = -1
    for (int i = 0; i < data.size() && firstFltIndex == -1; ++i) {
      if (data[i].flt_tm != 0.0 && data[i].gps_long != Double.NaN && data[i].gps_lat != Double.NaN) firstFltIndex = i
    }

    if (firstFltIndex != -1) {
      for (int i = data.size() - 1; i >= 0 && lastFltIndex == -1; --i) {
        if (data[i].flt_tm != 0.0 && data[i].gps_long != Double.NaN && data[i].gps_lat != Double.NaN) lastFltIndex = i
      }
    }
    if (firstFltIndex != -1 && lastFltIndex != -1) {
      takeOffTime = data[firstFltIndex].timeStamp
      takeOffLat = data[firstFltIndex].gps_lat
      takeOffLong = data[firstFltIndex].gps_long
      takeOffAlt = data[firstFltIndex].gps_alt
      landingTime = data[lastFltIndex].timeStamp
      landingLat = data[lastFltIndex].gps_lat
      landingLong = data[lastFltIndex].gps_long
      landingAlt = data[lastFltIndex].gps_alt
      flightDuration = Duration.between(takeOffTime, landingTime)
    }

    if (takeOffLat != null) {
      dist = distance(takeOffLat, takeOffLong, landingLat, LandingLong)
      startsAtHome = (distance(takeOffLat, takeOffLong, homeLat, homeLong) < 3.0)
    } else {
      dist = 0
      startsAtHome = false
    }
    if (landingLat != null) {
      endsAtHome = (distance(landingLat, landingLong, homeLat, homeLong) < 3.0)
    } else {
      dist = 0
      endsAtHome = false
    }

    avgSpeed = 0.0
    maxAlt = -100000
    if (flightDuration != null) {
      for (def i = firstFltIndex ; i <= lastFltIndex ; i++) {
        avgSpeed = avgSpeed + data[i].gps_speed
        maxAlt = data[i].gps_alt > maxAlt ? data[i].gps_alt : maxAlt
      }
      avgSpeed = avgSpeed / (lastFltIndex - firstFltIndex + 1)

      track = Math.round(avgSpeed * (flightDuration.toMillis() / 3600000))
    } else {
      avgSpeed = 0
      track = 0
    }

    loggingDuration = Duration.between(this.fltStart, data[data.size() - 1].timeStamp)

    integFuel = 0.0
    data.eachWithIndex { set, i ->
      if (i >= 1) {
        double time = Duration.between(data[i - 1].timeStamp, set.timeStamp).toMillis() / (1000.0 * 3600.0)
        if (time < 0) println "time: ${time}"
        if (set.f_flow < 0) println "set.f_flow: ${set.f_flow}"
        integFuel += set.f_flow * time
      }
    }
  }

  def dump() {
    data.eachWithIndex { set, i ->
      println "Set ${i}:"
      set.dump('    ', labels)
    }
  }

  def printTrack (file, header, footer, index, width, rich, icons, forGE, arrows, kmzDir, trackNo, arrowSize) {
    def lColors_w = ['ff7e649e', 'ffa7a700', 'ffb18ff3', 'ffb0279c', 'ff007cf5', 'ff7b18d2', 'ffd18802', 'ff177781',
                     'ff00d6ef', 'ffb73a67', 'ffda8a9f', 'ff0051c6', 'ff2f8b55', 'ff444444', 'ff4242ff', 'ff8dffff', 'ffee00ee']
    def lColors_e = ['fff01cce', 'ffa7a700', 'ffb18ff3', 'ffd18802', 'ffb0279c', 'ff007cf5', 'ff5b18c2', 'ff74b7e9',
                     'ffea4882', 'ff00c6df', 'ffda8a9f', 'ff0051c6', 'ff69b355', 'ffaaaaaa', 'ff4242ff', 'ff8dffff', 'ffee00ee']
    def lColors
    def iconScale
    if (forGE) {
      iconScale =  0.4 +  (arrowSize * arrowSize / 80) *  3
      lColors = lColors_e
    } else {
      iconScale = 1.0
      lColors = lColors_w
    }
    def iColor1 = '880E4F'
    def iColor1r = 'ff4f0e88'
    def iColor2 = '01579B'
    def iColor2r = 'ff9b5701'
    if (header) {
      file.println '<?xml version="1.0" encoding="UTF-8"?>'
      file.println '<kml xmlns="http://www.opengis.net/kml/2.2"'
      file.println ' xmlns:gx="http://www.google.com/kml/ext/2.2">'
      file.println '<Document>'
      file.println '    <LookAt>'
      file.println '      <gx:TimeSpan>'
      file.println "        <begin>${data[0].timeStamp}</begin>"
      file.println "        <end>${data[data.size()-1].timeStamp}</end>"
      file.println '      </gx:TimeSpan>'
      file.println "      <longitude>${data[data.size()>>1].gps_long}</longitude>"
      file.println "      <latitude>${data[data.size()>>1].gps_lat}</latitude>"
      file.println '      <altitude>100000</altitude>'
      file.println '      <range>1000000.0</range>'
      file.println '    </LookAt>'
      if (icons) {
        file.println "    <Style id=\"icon-1591-${iColor1}-nodesc-normal\">"
        file.println '      <IconStyle>'
        file.println "        <color>${iColor1r}</color>"
        file.println "        <scale>${iconScale}</scale>"
        file.println '        <Icon>'
        file.println '          <href>http://maps.google.com/mapfiles/kml/shapes/homegardenbusiness.png</href>'
        file.println '        </Icon>'
        file.println '      </IconStyle>'
        file.println '      <LabelStyle>'
        file.println '        <scale>0</scale>'
        file.println '      </LabelStyle>'
        file.println '      <BalloonStyle>'
        file.println '        <text><![CDATA[<h3>$[name]</h3>]]></text>'
        file.println '      </BalloonStyle>'
        file.println '    </Style>'
        file.println "    <Style id=\"icon-1591-${iColor1}-nodesc-highlight\">"
        file.println '      <IconStyle>'
        file.println "        <color>${iColor1r}</color>"
        file.println "        <scale>${iconScale}</scale>"
        file.println '        <Icon>'
        file.println '          <href>http://maps.google.com/mapfiles/kml/shapes/homegardenbusiness.png</href>'
        file.println '        </Icon>'
        file.println '      </IconStyle>'
        file.println '      <LabelStyle>'
        file.println "        <scale>${iconScale}</scale>"
        file.println '      </LabelStyle>'
        file.println '      <BalloonStyle>'
        file.println '        <text><![CDATA[<h3>$[name]</h3>]]></text>'
        file.println '      </BalloonStyle>'
        file.println '    </Style>'
        file.println "    <StyleMap id=\"icon-1591-${iColor1}-nodesc\">"
        file.println '      <Pair>'
        file.println '        <key>normal</key>'
        file.println "        <styleUrl>#icon-1591-${iColor1}-nodesc-normal</styleUrl>"
        file.println '      </Pair>'
        file.println '      <Pair>'
        file.println '        <key>highlight</key>'
        file.println "        <styleUrl>#icon-1591-${iColor1}-nodesc-highlight</styleUrl>"
        file.println '      </Pair>'
        file.println '    </StyleMap>'
        file.println "    <Style id=\"icon-1750-${iColor2}-nodesc-normal\">"
        file.println '      <IconStyle>'
        file.println "        <color>${iColor2r}</color>"
        file.println "        <scale>${iconScale}</scale>"
        file.println '        <Icon>'
        file.println '          <href>http://maps.google.com/mapfiles/kml/shapes/airports.png</href>'
        file.println '        </Icon>'
        file.println '      </IconStyle>'
        file.println '      <LabelStyle>'
        file.println '        <scale>0</scale>'
        file.println '      </LabelStyle>'
        file.println '      <BalloonStyle>'
        file.println '        <text><![CDATA[<h3>$[name]</h3>]]></text>'
        file.println '      </BalloonStyle>'
        file.println '    </Style>'
        file.println "    <Style id=\"icon-1750-${iColor2}-nodesc-highlight\">"
        file.println '      <IconStyle>'
        file.println "        <color>${iColor2r}</color>"
        file.println "        <scale>${iconScale}</scale>"
        file.println '        <Icon>'
        file.println '          <href>http://maps.google.com/mapfiles/kml/shapes/airports.png</href>'
        file.println '        </Icon>'
        file.println '      </IconStyle>'
        file.println '      <LabelStyle>'
        file.println "        <scale>${iconScale}</scale>"
        file.println '      </LabelStyle>'
        file.println '      <BalloonStyle>'
        file.println '        <text><![CDATA[<h3>$[name]</h3>]]></text>'
        file.println '      </BalloonStyle>'
        file.println '    </Style>'
        file.println "    <StyleMap id=\"icon-1750-${iColor2}-nodesc\">"
        file.println '      <Pair>'
        file.println '        <key>normal</key>'
        file.println "        <styleUrl>#icon-1750-${iColor2}-nodesc-normal</styleUrl>"
        file.println '      </Pair>'
        file.println '      <Pair>'
        file.println '        <key>highlight</key>'
        file.println "        <styleUrl>#icon-1750-${iColor2}-nodesc-highlight</styleUrl>"
        file.println '      </Pair>'
        file.println '    </StyleMap>'
      }
      for (def i = 0; i <= 16; ++ i) {
        file.println "    <Style id=\"multiTrack_n${i}\">"
        file.println '      <IconStyle>'
        file.println '      	<Icon>'
        file.println '      	</Icon>'
        file.println '      </IconStyle>'
        file.println '      <LineStyle>'
        file.println "        <color>${lColors[i]}</color>"
        file.println "        <width>${width}</width>"
        file.println '      </LineStyle>'
        file.println '    </Style>'
        file.println "    <Style id=\"multiTrack_h${i}\">"
        file.println '      <IconStyle>'
        file.println '      	<Icon>' // xxxx
        file.println '      	</Icon>'
        file.println '      </IconStyle>'
        file.println '      <LineStyle>'
        file.println "        <color>${lColors[i]}</color>"
        file.println "        <width>${width + 1}</width>"
        file.println '      </LineStyle>'
        file.println '    </Style>'
        file.println "    <StyleMap id=\"multiTrack${i}\">"
        file.println '      <Pair>'
        file.println '        <key>normal</key>'
        file.println "        <styleUrl>#multiTrack_n${i}</styleUrl>"
        file.println '      </Pair>'
        file.println '      <Pair>'
        file.println '        <key>highlight</key>'
        file.println "        <styleUrl>#multiTrack_h${i}</styleUrl>"
        file.println '      </Pair>'
        file.println '    </StyleMap>'
      }
      if (rich) {
        file.println '    <Schema id="schema">'
        file.println '      <gx:SimpleArrayField name="time" type="string">'
        file.println '        <displayName>Time</displayName>'
        file.println '      </gx:SimpleArrayField>'
        file.println '      <gx:SimpleArrayField name="gs" type="int">'
        file.println '        <displayName>GS</displayName>'
        file.println '      </gx:SimpleArrayField>'
        file.println '      <gx:SimpleArrayField name="altitude" type="int">'
        file.println '        <displayName>Altitude</displayName>'
        file.println '      </gx:SimpleArrayField>'
        file.println '      <gx:SimpleArrayField name="hp" type="int">'
        file.println '        <displayName>Power</displayName>'
        file.println '      </gx:SimpleArrayField>'
        file.println '      <gx:SimpleArrayField name="rpm" type="int">'
        file.println '        <displayName>RPM</displayName>'
        file.println '      </gx:SimpleArrayField>'
        file.println '      <gx:SimpleArrayField name="f_flow" type="float">'
        file.println '        <displayName>Fuel flow</displayName>'
        file.println '      </gx:SimpleArrayField>'
        file.println '      <gx:SimpleArrayField name="m_p" type="float">'
        file.println '        <displayName>Manifold Pressure</displayName>'
        file.println '      </gx:SimpleArrayField>'
        file.println '      <gx:SimpleArrayField name="oil_p" type="float">'
        file.println '        <displayName>Oil Pressure</displayName>'
        file.println '      </gx:SimpleArrayField>'
        file.println '      <gx:SimpleArrayField name="oil_t" type="float">'
        file.println '        <displayName>Oil Temperature</displayName>'
        file.println '      </gx:SimpleArrayField>'
        file.println '      <gx:SimpleArrayField name="egt1" type="float">'
        file.println '        <displayName>EGT1</displayName>'
        file.println '      </gx:SimpleArrayField>'
        file.println '      <gx:SimpleArrayField name="egt2" type="float">'
        file.println '        <displayName>EGT2</displayName>'
        file.println '      </gx:SimpleArrayField>'
        file.println '      <gx:SimpleArrayField name="egt3" type="float">'
        file.println '        <displayName>EGT3</displayName>'
        file.println '      </gx:SimpleArrayField>'
        file.println '      <gx:SimpleArrayField name="egt4" type="float">'
        file.println '        <displayName>EGT4</displayName>'
        file.println '      </gx:SimpleArrayField>'
        file.println '      <gx:SimpleArrayField name="egt5" type="float">'
        file.println '        <displayName>EGT5</displayName>'
        file.println '      </gx:SimpleArrayField>'
        file.println '      <gx:SimpleArrayField name="egt6" type="float">'
        file.println '        <displayName>EGT6</displayName>'
        file.println '      </gx:SimpleArrayField>'
        file.println '      <gx:SimpleArrayField name="oat" type="float">'
        file.println '        <displayName>OAT</displayName>'
        file.println '      </gx:SimpleArrayField>'
        file.println '      <gx:SimpleArrayField name="tit" type="float">'
        file.println '        <displayName>TIT</displayName>'
        file.println '      </gx:SimpleArrayField>'
        file.println '      <gx:SimpleArrayField name="cht1" type="float">'
        file.println '        <displayName>CHT1</displayName>'
        file.println '      </gx:SimpleArrayField>'
        file.println '      <gx:SimpleArrayField name="cht2" type="float">'
        file.println '        <displayName>CHT2</displayName>'
        file.println '      </gx:SimpleArrayField>'
        file.println '      <gx:SimpleArrayField name="cht3" type="float">'
        file.println '        <displayName>CHT3</displayName>'
        file.println '      </gx:SimpleArrayField>'
        file.println '      <gx:SimpleArrayField name="cht4" type="float">'
        file.println '        <displayName>CHT4</displayName>'
        file.println '      </gx:SimpleArrayField>'
        file.println '      <gx:SimpleArrayField name="cht5" type="float">'
        file.println '        <displayName>CHT5</displayName>'
        file.println '      </gx:SimpleArrayField>'
        file.println '      <gx:SimpleArrayField name="cht6" type="float">'
        file.println '        <displayName>CHT6</displayName>'
        file.println '      </gx:SimpleArrayField>'
        file.println '    </Schema>'
      }
      file.println '  <Folder>'
    }
    file.println '    <Placemark>'
    if (!forGE) {
      file.println "    <name>Flt ${fltNum} ${fltStart}</name>"
    }
    file.println "    <styleUrl>#multiTrack${index}</styleUrl>"
    if (!rich) {
      file.println '      <LineString>'
      file.println '        <extrude>0</extrude>'
      file.println '        <tessellate>0</tessellate>'
      file.println '        <altitudeMode>absolute</altitudeMode>'
      file.println '        <coordinates>'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          ${set.gps_long},${set.gps_lat},${set.gps_alt * (12 * 0.0254)}"
        }
      }
      file.println '        </coordinates>'
      file.println '      </LineString>'
    } else {
      file.println '      <gx:Track>'
      file.println '        <gx:altitudeMode>absolute</gx:altitudeMode>'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "        <gx:when>${set.timeStamp}</gx:when>"
        }
      }
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println '      <gx:coord>' + set.gps_long + ' ' + set.gps_lat + ' ' + (set.gps_alt * (12 * 0.0254))  + '</gx:coord>'
        }
      }
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println '      <gx:angles>' + set.gps_track  + '0.0 0.0 </gx:angles>'
        }
      }
      file.println '    <ExtendedData>'
      file.println '      <SchemaData schemaUrl="#schema">'

      file.println '        <gx:SimpleArrayData name="time">'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          <gx:value>${set.timeStamp}</gx:value>"
        }
      }
      file.println '        </gx:SimpleArrayData>'

      file.println '        <gx:SimpleArrayData name="gs">'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          <gx:value>${set.gps_speed}</gx:value>"
        }
      }
      file.println '        </gx:SimpleArrayData>'

      file.println '        <gx:SimpleArrayData name="altitude">'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          <gx:value>${set.gps_alt}</gx:value>"
        }
      }
      file.println '        </gx:SimpleArrayData>'

      file.println '        <gx:SimpleArrayData name="hp">'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          <gx:value>${set.hp}</gx:value>"
        }
      }
      file.println '        </gx:SimpleArrayData>'

      file.println '        <gx:SimpleArrayData name="rpm">'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          <gx:value>${set.rpm}</gx:value>"
        }
      }
      file.println '        </gx:SimpleArrayData>'

      file.println '        <gx:SimpleArrayData name="f_flow">'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          <gx:value>${set.f_flow}</gx:value>"
        }
      }
      file.println '        </gx:SimpleArrayData>'

      file.println '        <gx:SimpleArrayData name="m_p">'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          <gx:value>${set.m_p}</gx:value>"
        }
      }
      file.println '        </gx:SimpleArrayData>'

      file.println '        <gx:SimpleArrayData name="oil_p">'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          <gx:value>${set.oil_p}</gx:value>"
        }
      }
      file.println '        </gx:SimpleArrayData>'

      file.println '        <gx:SimpleArrayData name="oil_t">'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          <gx:value>${set.oil_t}</gx:value>"
        }
      }
      file.println '        </gx:SimpleArrayData>'

      file.println '        <gx:SimpleArrayData name="egt1">'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          <gx:value>${set.egt1}</gx:value>"
        }
      }
      file.println '        </gx:SimpleArrayData>'

      file.println '        <gx:SimpleArrayData name="egt2">'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          <gx:value>${set.egt2}</gx:value>"
        }
      }
      file.println '        </gx:SimpleArrayData>'

      file.println '        <gx:SimpleArrayData name="egt3">'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          <gx:value>${set.egt3}</gx:value>"
        }
      }
      file.println '        </gx:SimpleArrayData>'

      file.println '        <gx:SimpleArrayData name="egt4">'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          <gx:value>${set.egt4}</gx:value>"
        }
      }
      file.println '        </gx:SimpleArrayData>'

      file.println '        <gx:SimpleArrayData name="egt5">'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          <gx:value>${set.egt5}</gx:value>"
        }
      }
      file.println '        </gx:SimpleArrayData>'

      file.println '        <gx:SimpleArrayData name="egt6">'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          <gx:value>${set.egt6}</gx:value>"
        }
      }
      file.println '        </gx:SimpleArrayData>'

      file.println '        <gx:SimpleArrayData name="oat">'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          <gx:value>${set.oat}</gx:value>"
        }
      }
      file.println '        </gx:SimpleArrayData>'

      file.println '        <gx:SimpleArrayData name="tit">'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          <gx:value>${set.tit}</gx:value>"
        }
      }
      file.println '        </gx:SimpleArrayData>'

      file.println '        <gx:SimpleArrayData name="cht1">'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          <gx:value>${set.cht1}</gx:value>"
        }
      }
      file.println '        </gx:SimpleArrayData>'

      file.println '        <gx:SimpleArrayData name="cht2">'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          <gx:value>${set.cht2}</gx:value>"
        }
      }
      file.println '        </gx:SimpleArrayData>'

      file.println '        <gx:SimpleArrayData name="cht3">'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          <gx:value>${set.cht3}</gx:value>"
        }
      }
      file.println '        </gx:SimpleArrayData>'

      file.println '        <gx:SimpleArrayData name="cht4">'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          <gx:value>${set.cht4}</gx:value>"
        }
      }
      file.println '        </gx:SimpleArrayData>'

      file.println '        <gx:SimpleArrayData name="cht5">'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          <gx:value>${set.cht5}</gx:value>"
        }
      }
      file.println '        </gx:SimpleArrayData>'

      file.println '        <gx:SimpleArrayData name="cht6">'
      data.each { set ->
        if (!Double.isNaN(set.gps_lat) && !Double.isNaN(set.gps_long)) {
          file.println "          <gx:value>${set.cht6}</gx:value>"
        }
      }
      file.println '        </gx:SimpleArrayData>'
      file.println '      </SchemaData>'
      file.println '    </ExtendedData>'

      file.println '      </gx:Track>'
    }
    file.println '    </Placemark>'

    if (arrows) {
      def position = data.size() >> 1
      def d = 0
      while ((Double.isNaN(data[position + d].gps_lat) || Double.isNaN(data[position + d].gps_long)) && (position  + d) > 0 && (position  + d) < (data.size() - 1)) {
        d = -d
        if ((Double.isNaN(data[position + d].gps_lat) || Double.isNaN(data[position  + d].gps_long)) && (position  + d) > 0 && (position  + d) < (data.size() - 1)) d = -d + 1
      }
      position += d
      if (position >= 0 && position < data.size()) {
        def start = position - 3
        def end = position + 3

        while (start >= 0 && (Double.isNaN(data[start].gps_lat) || Double.isNaN(data[start].gps_long))) start--
        while (end < data.size() && (Double.isNaN(data[end].gps_lat) || Double.isNaN(data[end].gps_long))) end++

        if (start >= 0 && end < data.size()) {
          def track = coordTrack(data[start].gps_lat, data[start].gps_long, data[end].gps_lat, data[end].gps_long)
          def xHot
          def yHot
          def icon
          if (forGE) {
            xHot = 0.5
            yHot = 1.0
            icon = "arrow.png"
          } else {
            xHot = (Math.sin(Math.toRadians(track)) * ((16.0 - (9.0 - arrowSize)) / 16.0) + 1) / 2
            yHot = (Math.cos(Math.toRadians(track)) * ((16.0 - (9.0 - arrowSize)) / 16.0) + 1) / 2
            BufferedImage bi = new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB)
            for (def x = 0; x < 32; x++) for (def y = 0; y < 16; y++) bi.setRGB(x, y, 0)
            def colStr = lColors[index]
            colStr = colStr.substring(0, 2) + colStr.substring(6, 8) + colStr.substring(4, 6) + colStr.substring(2, 4)
            int color = Integer.parseUnsignedInt(colStr, 16)
            for (def j = (9 - arrowSize); j < 16; j++) {
              for (def i = 0; i < (16 - j); i++) {
                bi.setRGB(16+i, i + j, color)
                bi.setRGB(16-i, i + j, color)
              }
            }
            BufferedImage rotate = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphic = rotate.createGraphics();
            graphic.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphic.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            graphic.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            graphic.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphic.rotate(Math.toRadians(track), 16, 16);
            graphic.drawImage(bi, 0, 0, null);
            graphic.dispose();
            icon = "a${trackNo}.png"
            ImageIO.write(rotate, "png", new File("${kmzDir}/images/${icon}"))
          }

          file.println '    <Placemark>'
          file.println "     <Style>"
          file.println "        <IconStyle>"
          file.println "        <color>${lColors[index]}</color>"
          if (forGE) {
            file.println "        <heading>${track}</heading>"
          }
          file.println "          <scale>${iconScale}</scale>"
          file.println "          <Icon>"
          file.println "            <href>images/${icon}</href>"
          file.println "          </Icon>"
          file.println "          <hotSpot x=\"${xHot}\" y=\"${yHot}\" xunits=\"fraction\" yunits=\"fraction\"/>"
          file.println "        </IconStyle>"
          file.println "     </Style>"
          file.println '     <Point>'
          file.println '       <altitudeMode>absolute</altitudeMode>'
          file.println '       <coordinates>'
          file.println "         ${data[position].gps_long},${data[position].gps_lat},${data[position].gps_alt * (12 * 0.0254)}"
          file.println '       </coordinates>'
          file.println '     </Point>'
          file.println '    </Placemark>'
        }
      }
    }
    if (icons && !endsAtHome) {
      file.println '    <Placemark>'
      if (!forGE) {
        file.println '     <name>AD</name>'
        file.println "     <styleUrl>#icon-1750-${iColor2}-nodesc</styleUrl>"
      } else {
        def ldgTrack = 0
        if (lastFltIndex != -1) {
          def i = lastFltIndex - 6
          while ((Double.isNaN(data[i].gps_lat) || Double.isNaN(data[i].gps_long)) && i > 0) i--
          ldgTrack = coordTrack(data[i].gps_lat, data[i].gps_long, data[lastFltIndex].gps_lat, data[lastFltIndex].gps_long)
        }
        file.println "     <Style>"
        file.println "        <IconStyle>"
        file.println "        <heading>${ldgTrack}</heading>"
        file.println "          <scale>${iconScale}</scale>"
        file.println "          <Icon>"
        if (forGE) {
          file.println '          <href>images/ad.png</href>'
        } else {
          file.println '          <href>http://maps.google.com/mapfiles/kml/shapes/airports.png</href>'
        }
        file.println "          </Icon>"
        file.println "        </IconStyle>"
        file.println "     </Style>"
      }
      file.println '     <Point>'
      file.println '      <coordinates>'
      file.println "      ${landingLong},${landingLat}"
      file.println '      </coordinates>'
      file.println '     </Point>'
      file.println '    </Placemark>'
    }
    if (footer) {
      if (icons) {
        file.println '    <Placemark>'
        file.println '     <name>Home</name>'
        file.println "     <styleUrl>#icon-1591-${iColor1}-nodesc</styleUrl>"
        file.println '     <Point>'
        file.println '      <coordinates>'
        file.println "      ${homeLong},${homeLat}"
        file.println '      </coordinates>'
        file.println '     </Point>'
        file.println '    </Placemark>'
      }
      file.println '  </Folder>'
      file.println '</Document>'
      file.println '</kml>'
    }
  }
}

class TimeStampedSet
implements Comparable<TimeStampedSet> {
  def timeStamp
  String time
  int mstr_wrn
  int rpm
  float f_flow
  float fuel_l
  float fuel_r
  float volts
  float amps
  float m_p
  int oil_p
  int egt1
  int egt2
  int egt3
  int egt4
  int egt5
  int egt6
  int oil_t
  int oat
  int tit
  int cht1
  int cht2
  int cht3
  int cht4
  int cht5
  int cht6
  String t_comp1
  String t_comp2
  int hp
  int s_cool
  float est_fuel
  float flt_tm
  float eng_hrs
  float tach_tm
  String gps_utc
  String gps_waypt
  double gps_lat
  double gps_long
  int gps_speed
  int gps_alt
  float gps_track
  int gps_qlty
  int gps_numsat
  int auxin1
  int auxin2
  int auxin3

  TimeStampedSet(timeSt, values, labels) {
    timeStamp = timeSt
    values.eachWithIndex { value, index ->
      switch (index) {
        case labels.timePos:
          time = value
          break
        case labels.mstr_wrnPos:
          mstr_wrn = Integer.parseInt(value)
          break
        case labels.rpmPos:
          rpm = Integer.parseInt(value)
          break
        case labels.f_flowPos:
          f_flow = Float.parseFloat(value)
          break
        case labels.fuel_lPos:
          fuel_l = Float.parseFloat(value)
          break
        case labels.fuel_rPos:
          fuel_r = Float.parseFloat(value)
          break
        case labels.voltsPos:
          volts = Float.parseFloat(value)
          break
        case labels.ampsPos:
          amps = Float.parseFloat(value)
          break
        case labels.m_pPos:
          m_p = Float.parseFloat(value)
          break
        case labels.oil_pPos:
          oil_p = Integer.parseInt(value)
          break
        case labels.egt1Pos:
          egt1 = Integer.parseInt(value)
          break
        case labels.egt2Pos:
          egt2 = Integer.parseInt(value)
          break
        case labels.egt3Pos:
          egt3 = Integer.parseInt(value)
          break
        case labels.egt4Pos:
          egt4 = Integer.parseInt(value)
          break
        case labels.egt5Pos:
          egt5 = Integer.parseInt(value)
          break
        case labels.egt6Pos:
          egt6 = Integer.parseInt(value)
          break
        case labels.oil_tPos:
          oil_t = Integer.parseInt(value)
          break
        case labels.oatPos:
          oat = Integer.parseInt(value)
          break
        case labels.titPos:
          tit = Integer.parseInt(value)
          break
        case labels.cht1Pos:
          cht1 = Integer.parseInt(value)
          break
        case labels.cht2Pos:
          cht2 = Integer.parseInt(value)
          break
        case labels.cht3Pos:
          cht3 = Integer.parseInt(value)
          break
        case labels.cht4Pos:
          cht4 = Integer.parseInt(value)
          break
        case labels.cht5Pos:
          cht5 = Integer.parseInt(value)
          break
        case labels.cht6Pos:
          cht6 = Integer.parseInt(value)
          break
        case labels.t_comp1Pos:
          t_comp1 = value
          break
        case labels.t_comp2Pos:
          t_comp2 = value
          break
        case labels.hpPos:
          hp = Integer.parseInt(value)
          break
        case labels.s_coolPos:
          s_cool = Integer.parseInt(value)
          break
        case labels.est_fuelPos:
          est_fuel = Float.parseFloat(value)
          break
        case labels.flt_tmPos:
          flt_tm = Float.parseFloat(value)
          break
        case labels.eng_hrsPos:
          eng_hrs = Float.parseFloat(value)
          break
        case labels.tach_tmPos:
          tach_tm = Float.parseFloat(value)
          break
        case labels.gps_utcPos:
          gps_utc = value
          break
        case labels.gps_wayptPos:
          gps_waypt = value
          break
        case labels.gps_latPos:
          gps_lat = parseCoord(value)
          break
        case labels.gps_longPos:
          gps_long = parseCoord(value)
          break
        case labels.gps_speedPos:
          if (value =~ /---/) gps_speed = -1 else gps_speed = Integer.parseInt(value)
          break
        case labels.gps_altPos:
          if (value =~ /---/) gps_alt = -1 else gps_alt = Integer.parseInt(value)
          break
        case labels.gps_trackPos:
          if (value =~ /---/) gps_track = Float.NaN else gps_track = Float.parseFloat(value)
          break
        case labels.gps_qltyPos:
          gps_qlty = Integer.parseInt(value)
          break
        case labels.gps_numsatPos:
          gps_numsat = Integer.parseInt(value)
          break
        case labels.auxin1Pos:
          auxin1 = Integer.parseInt(value)
          break
        case labels.auxin2Pos:
          auxin2 = Integer.parseInt(value)
          break
        case labels.auxin3Pos:
          auxin3 = Integer.parseInt(value)
          break
      }
    }
  }

  TimeStampedSet(val) {
    def f = val[0]
    timeStamp = f.timeStamp
    def d = val.size()
    time = f.time
    rpm = f.rpm
    f_flow = f.f_flow
    fuel_l = f.fuel_l
    fuel_r = f.fuel_r
    volts = f.volts
    amps = f.amps
    m_p = f.m_p
    oil_p = f.oil_p
    egt1 = f.egt1
    egt2 = f.egt2
    egt3 = f.egt3
    egt4 = f.egt4
    egt5 = f.egt5
    egt6 = f.egt6
    oil_t = f.oil_t
    oat = f.oat
    tit = f.tit
    cht1 = f.cht1
    cht2 = f.cht2
    cht3 = f.cht3
    cht4 = f.cht4
    cht5 = f.cht5
    cht6 = f.cht6
    t_comp1 = f.t_comp1
    t_comp2 = f.t_comp2
    hp = f.hp
    s_cool = f.s_cool
    est_fuel = f.est_fuel
    flt_tm = f.flt_tm
    eng_hrs = f.eng_hrs
    tach_tm = f.tach_tm
    gps_utc = f.gps_utc
    gps_waypt = f.gps_waypt
    gps_lat = f.gps_lat
    gps_long = f.gps_long
    gps_speed = f.gps_speed
    gps_alt = f.gps_alt
    gps_track = f.gps_track
    gps_qlty = f.gps_qlty
    gps_numsat = f.gps_numsat
    auxin1 = f.auxin1
    auxin2 = f.auxin2
    auxin3 = f.auxin3
    mstr_wrn = f.mstr_wrn

    for (int i = 1; i < d; ++i) {
      rpm += val[i].rpm
      f_flow += val[i].f_flow
      fuel_l += val[i].fuel_l
      fuel_r += val[i].fuel_r
      volts += val[i].volts
      amps += val[i].amps
      m_p += val[i].m_p
      oil_p += val[i].oil_p
      egt1 += val[i].egt1
      egt2 += val[i].egt2
      egt3 += val[i].egt3
      egt4 += val[i].egt4
      egt5 += val[i].egt5
      egt6 += val[i].egt6
      oil_t += val[i].oil_t
      oat += val[i].oat
      tit += val[i].tit
      cht1 += val[i].cht1
      cht2 += val[i].cht2
      cht3 += val[i].cht3
      cht4 += val[i].cht4
      cht5 += val[i].cht5
      cht6 += val[i].cht6
      hp += val[i].hp
      s_cool += val[i].s_cool
      est_fuel += val[i].est_fuel
      flt_tm += val[i].flt_tm
      eng_hrs += val[i].eng_hrs
      tach_tm += val[i].tach_tm
      gps_lat += val[i].gps_lat
      gps_long += val[i].gps_long
      gps_speed += val[i].gps_speed
      gps_alt += val[i].gps_alt
      gps_track += val[i].gps_track
      gps_qlty += val[i].gps_qlty
      gps_numsat += val[i].gps_numsat
      auxin1 += val[i].auxin1
      auxin2 += val[i].auxin2
      auxin3 += val[i].auxin3
    }
    rpm /= d
    f_flow /= d
    fuel_l /= d
    fuel_r /= d
    volts /= d
    amps /= d
    m_p /= d
    oil_p /= d
    egt1 /= d
    egt2 /= d
    egt3 /= d
    egt4 /= d
    egt5 /= d
    egt6 /= d
    oil_t /= d
    oat /= d
    tit /= d
    cht1 /= d
    cht2 /= d
    cht3 /= d
    cht4 /= d
    cht5 /= d
    cht6 /= d
    hp /= d
    s_cool /= d
    est_fuel /= d
    flt_tm /= d
    eng_hrs /= d
    tach_tm /= d
    gps_lat /= d
    gps_long /= d
    gps_speed /= d
    gps_alt /= d
    gps_track /= d
    gps_qlty /= d
    gps_numsat /= d
    auxin1 /= d
    auxin2 /= d
    auxin3 /= d
  }

  def double parseCoord(String s) {
    double c
    try {
      def fields = s.split(' ')

      double deg = Double.parseDouble(fields[1])
      double sec = Double.parseDouble(fields[2]) / 100.0
      c = deg + sec / 60.0
      if (fields[0] == 'S' || fields[0] == 'W') c = -c
    }
    catch (java.lang.NumberFormatException e) {
      c = Double.NaN
    }
    catch (java.lang.ArrayIndexOutOfBoundsException e) {
      c = Double.NaN
    }
    return c
  }

  def dump(pref, labels) {
    def i = 0
    println pref + timeStamp
    println pref + labels.names[i++] + ': ' + time
    println pref + labels.names[i++] + ': ' + mstr_wrn
    println pref + labels.names[i++] + ': ' + rpm + labels.rpmUnit
    println pref + labels.names[i++] + ': ' + f_flow + labels.f_flowUnit
    println pref + labels.names[i++] + ': ' + fuel_l + labels.fuel_lUnit
    println pref + labels.names[i++] + ': ' + fuel_r + labels.fuel_rUnit
    println pref + labels.names[i++] + ': ' + volts + labels.voltsUnit
    println pref + labels.names[i++] + ': ' + amps + labels.ampsUnit
    println pref + labels.names[i++] + ': ' + m_p + labels.m_pUnit
    println pref + labels.names[i++] + ': ' + oil_p + labels.oil_pUnit
    println pref + labels.names[i++] + ': ' + egt1 + labels.egt1Unit
    println pref + labels.names[i++] + ': ' + egt2 + labels.egt2Unit
    println pref + labels.names[i++] + ': ' + egt3 + labels.egt3Unit
    println pref + labels.names[i++] + ': ' + egt4 + labels.egt4Unit
    println pref + labels.names[i++] + ': ' + egt5 + labels.egt5Unit
    println pref + labels.names[i++] + ': ' + egt6 + labels.egt6Unit
    println pref + labels.names[i++] + ': ' + oil_t + labels.oil_tUnit
    println pref + labels.names[i++] + ': ' + oat + labels.oatUnit
    println pref + labels.names[i++] + ': ' + tit + labels.titUnit
    println pref + labels.names[i++] + ': ' + cht1 + labels.cht1Unit
    println pref + labels.names[i++] + ': ' + cht2 + labels.cht2Unit
    println pref + labels.names[i++] + ': ' + cht3 + labels.cht3Unit
    println pref + labels.names[i++] + ': ' + cht4 + labels.cht4Unit
    println pref + labels.names[i++] + ': ' + cht5 + labels.cht5Unit
    println pref + labels.names[i++] + ': ' + cht6 + labels.cht6Unit
    println pref + labels.names[i++] + ': ' + t_comp1
    println pref + labels.names[i++] + ': ' + t_comp2
    println pref + labels.names[i++] + ': ' + hp + labels.hpUnit
    println pref + labels.names[i++] + ': ' + s_cool + labels.s_coolUnit
    println pref + labels.names[i++] + ': ' + est_fuel + labels.est_fuelUnit
    println pref + labels.names[i++] + ': ' + flt_tm + labels.flt_tmUnit
    println pref + labels.names[i++] + ': ' + eng_hrs + labels.eng_hrsUnit
    println pref + labels.names[i++] + ': ' + tach_tm + labels.tach_tmUnit
    println pref + labels.names[i++] + ': ' + gps_utc
    println pref + labels.names[i++] + ': ' + gps_waypt
    println pref + labels.names[i++] + ': ' + gps_lat
    println pref + labels.names[i++] + ': ' + gps_long
    println pref + labels.names[i++] + ': ' + gps_speed + labels.gps_speedUnit
    println pref + labels.names[i++] + ': ' + gps_alt + labels.gps_altUnit
    println pref + labels.names[i++] + ': ' + gps_track + labels.gps_trackUnit
    println pref + labels.names[i++] + ': ' + gps_qlty
    println pref + labels.names[i++] + ': ' + gps_numsat
    println pref + labels.names[i++] + ': ' + auxin1
    println pref + labels.names[i++] + ': ' + auxin2
    println pref + labels.names[i++] + ': ' + auxin3
  }

  public int compareTo(TimeStampedSet set) {
    int i = timeStamp.compareTo(set.timeStamp())
    if (i != 0) return i
    return filename().compareTo(set.timeStamp())
  }

}

class Labels {

  // TIME,MSTR_WRN,RPM;RPM,F.FLOW;GPH,FUEL L;GAL,FUEL R;GAL,VOLTS;V,AMPS;A,M.P.;HG,OIL P;PSI,
  // EGT 1;*F,EGT 2;*F,EGT 3;*F,EGT 4;*F,EGT 5;*F,EGT 6;*F,OIL T;*F,OAT;*F,TIT;*F,CHT 1;*F,CHT 2;*F,CHT 3;*F,CHT 4;*F,CHT 5;*F,CHT 6;*F,
  // T.COMP1;,T.COMP2;,HP;%,S.COOL;*F/M,EST FUEL;GAL,FLT TM;MIN,ENG HRS;HRS,TACH TM;HRS,GPS-UTC,GPS-WAYPT,GPS-LAT,GPS-LONG,GPS-SPEED;KTS,
  // GPS-ALT;F,GPS-TRACK;DEG,GPS-QLTY,GPS-NumSat,AUXIN1,AUXIN2,AUXIN3,

  def timePos
  def mstr_wrnPos
  def rpmPos
  def rpmUnit
  def f_flowPos
  def f_flowUnit
  def fuel_lPos
  def fuel_lUnit
  def fuel_rPos
  def fuel_rUnit
  def voltsPos
  def voltsUnit
  def ampsPos
  def ampsUnit
  def m_pPos
  def m_pUnit
  def oil_pPos
  def oil_pUnit
  def egt1Pos
  def egt1Unit
  def egt2Pos
  def egt2Unit
  def egt3Pos
  def egt3Unit
  def egt4Pos
  def egt4Unit
  def egt5Pos
  def egt5Unit
  def egt6Pos
  def egt6Unit
  def oil_tPos
  def oil_tUnit
  def oatPos
  def oatUnit
  def titPos
  def titUnit
  def cht1Pos
  def cht1Unit
  def cht2Pos
  def cht2Unit
  def cht3Pos
  def cht3Unit
  def cht4Pos
  def cht4Unit
  def cht5Pos
  def cht5Unit
  def cht6Pos
  def cht6Unit
  def t_comp1Pos
  def t_comp2Pos
  def hpPos
  def hpUnit
  def s_coolPos
  def s_coolUnit
  def est_fuelPos
  def est_fuelUnit
  def flt_tmPos
  def flt_tmUnit
  def eng_hrsPos
  def eng_hrsUnit
  def tach_tmPos
  def tach_tmUnit
  def gps_utcPos
  def gps_wayptPos
  def gps_latPos
  def gps_longPos
  def gps_speedPos
  def gps_speedUnit
  def gps_altPos
  def gps_altUnit
  def gps_trackPos
  def gps_trackUnit
  def gps_qltyPos
  def gps_numsatPos
  def auxin1Pos
  def auxin2Pos
  def auxin3Pos

  def names = []

  Labels(line) {
    def fields
    fields = line.split(/,/)
    fields.eachWithIndex { field, index ->
      def fieldEntry
      fieldEntry = field.split(/;/)
      def label = fieldEntry[0]
      if (label == 'TIME') { timePos = index }
      if (label == 'MSTR_WRN') { mstr_wrnPos = index }
      if (label == 'RPM') { rpmPos = index ; rpmUnit = fieldEntry[1] }
      if (label == 'F.FLOW') { f_flowPos = index ; f_flowUnit = fieldEntry[1] }
      if (label == 'FUEL L') { fuel_lPos = index ; fuel_lUnit = fieldEntry[1] }
      if (label == 'FUEL R') { fuel_rPos = index ; fuel_rUnit = fieldEntry[1] }
      if (label == 'VOLTS') { voltsPos = index ; voltsUnit = fieldEntry[1] }
      if (label == 'AMPS') { ampsPos = index ; ampsUnit = fieldEntry[1] }
      if (label == 'M.P.') { m_pPos = index ; m_pUnit = fieldEntry[1] }
      if (label == 'OIL P') { oil_pPos = index ; oil_pUnit = fieldEntry[1] }
      if (label == 'EGT 1') { egt1Pos = index ; egt1Unit = fieldEntry[1] }
      if (label == 'EGT 2') { egt2Pos = index ; egt2Unit = fieldEntry[1] }
      if (label == 'EGT 3') { egt3Pos = index ; egt3Unit = fieldEntry[1] }
      if (label == 'EGT 4') { egt4Pos = index ; egt4Unit = fieldEntry[1] }
      if (label == 'EGT 5') { egt5Pos = index ; egt5Unit = fieldEntry[1] }
      if (label == 'EGT 6') { egt6Pos = index ; egt6Unit = fieldEntry[1] }
      if (label == 'OIL T') { oil_tPos = index ; oil_tUnit = fieldEntry[1] }
      if (label == 'OAT') { oatPos = index ; oatUnit = fieldEntry[1] }
      if (label == 'TIT') { titPos = index ; titUnit = fieldEntry[1] }
      if (label == 'CHT 1') { cht1Pos = index ; cht1Unit = fieldEntry[1] }
      if (label == 'CHT 2') { cht2Pos = index ; cht2Unit = fieldEntry[1] }
      if (label == 'CHT 3') { cht3Pos = index ; cht3Unit = fieldEntry[1] }
      if (label == 'CHT 4') { cht4Pos = index ; cht4Unit = fieldEntry[1] }
      if (label == 'CHT 5') { cht5Pos = index ; cht5Unit = fieldEntry[1] }
      if (label == 'CHT 6') { cht6Pos = index ; cht6Unit = fieldEntry[1] }
      if (label == 'T.COMP1') { t_comp1Pos = index }
      if (label == 'T.COMP2') { t_comp2Pos = index }
      if (label == 'HP') { hpPos = index ; hpUnit = fieldEntry[1] }
      if (label == 'S.COOL') { s_coolPos = index ; s_coolUnit = fieldEntry[1] }
      if (label == 'EST FUEL') { est_fuelPos = index ; est_fuelUnit = fieldEntry[1] }
      if (label == 'FLT TM') { flt_tmPos = index ; flt_tmUnit = fieldEntry[1] }
      if (label == 'ENG HRS') { eng_hrsPos = index ; eng_hrsUnit = fieldEntry[1] }
      if (label == 'TACH TM') { tach_tmPos = index ; tach_tmUnit = fieldEntry[1] }
      if (label == 'GPS-UTC') { gps_utcPos = index }
      if (label == 'GPS-WAYPT') { gps_wayptPos = index }
      if (label == 'GPS-LAT') { gps_latPos = index }
      if (label == 'GPS-LONG') { gps_longPos = index }
      if (label == 'GPS-SPEED') { gps_speedPos = index ; gps_speedUnit = fieldEntry[1] }
      if (label == 'GPS-ALT') { gps_altPos = index ; gps_altUnit = fieldEntry[1] }
      if (label == 'GPS-TRACK') { gps_trackPos = index ; gps_trackUnit = fieldEntry[1] }
      if (label == 'GPS-QLTY') { gps_qltyPos = index }
      if (label == 'GPS-NumSat') { gps_numsatPos = index }
      if (label == 'AUXIN1') { auxin1Pos = index }
      if (label == 'AUXIN2') { auxin2Pos = index }
      if (label == 'AUXIN3') { auxin3Pos = index }

      names << label
    }
  }

}
