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
    println '           -b int  icon/arrow size, with -e affects all, without only arrows (0 to 9, default 4)'
    println '           -c int  color scheme in kml (0: mono, 1: color full, 2: journeys)'
    println '           -e      optimize kml for Google Earth'
    println '           -j      icons in kml'
    println '           -m      mix up colors in kml'
    println '           -o      omit local flights in kml'
    println '           -r      output rich kml (for interactive use of GE)'
    println '           -u int  position update interval in kml (default 2)'
    println '           -w int  line width of tracks in kml (default 5)'
    println '           -x int  smoothen criss cross of tracks in kml (0 to 5, 0 no smoothening, default 1)'
    println '           -s int  scale factor for initial kml view (and for moving icon tour, default 3000)'
    println '           -t int  generate kml moving icon tour of int seconds (default 0, implies -e)'
    println '           -z      zoom in on kml moving icon tour (optical effect)'
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
      def fuelUsed = false
      def information = false
      def exit = false
      def kmlFileName = ''
      def kmlDirArrows = false
      def kmlDirArrowSize = 4
      def csvFileNames = []
      def ignore = 0
      def numFiles = 0
      def kmlStep = 2
      def kmlWidth = 5
      def kmlForGE = false
      def kmlIcons = false
      def richKml = false
      def kmlColorScheme = 0
      def kmlOmitLocals = false
      def mixColors = false
      def kmlTourDuration = 0
      def kmlScaleFactor = 3000
      def kmlZoomIn = false
      def kmlSmoothening = 2
      def deck = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]
      def flights = []

      args.eachWithIndex { arg, i ->
        if (ignore == 0) {
          switch (arg) {
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
              if (args.length > i + 1) {
                kmlTourDuration = args[i + 1] as int
                if (kmlTourDuration > 0) {
                  kmlForGE = true
                }
              }
              ignore = 1
              break
            case '-z' :
              kmlZoomIn = true
              break
            case'-s' :
              if (args.length > i + 1) {
                kmlScaleFactor = args[i + 1] as int
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
            case '-x' :
              if (args.length > i + 1) {
                kmlSmoothening = (args[i + 1] as int) + 1
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
            def adIconString = "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"
            def adIconBytes = Base64.getDecoder().decode(adIconString)
            OutputStream stream = new FileOutputStream("${kmzDir}/images/ad.png")
            stream.write(adIconBytes)
            if (kmlTourDuration > 0) {
              def acIconString = "iVBORw0KGgoAAAANSUhEUgAAAIsAAABnCAYAAAAwn7EGAAABhGlDQ1BJQ0MgcHJvZmlsZQAAKJF9kT1Iw0AcxV/TSkUqCnYQKZihOtlFRRxrFYpQIdQKrTqYXPoFTRqSFBdHwbXg4Mdi1cHFWVcHV0EQ/ABxdnBSdJES/5cWWsR4cNyPd/ced+8AoVFhmhWIA5pum+lkQszmVsXgK0KIYBABjMrMMuYkKQXP8XUPH1/vYjzL+9yfo1/NWwzwicRxZpg28QbxzKZtcN4nDrOSrBKfE0+YdEHiR64rLX7jXHRZ4JlhM5OeJw4Ti8UuVrqYlUyNeJo4qmo65QvZFquctzhrlRpr35O/MJTXV5a5TjOCJBaxBAkiFNRQRgU2YrTqpFhI037Cwz/i+iVyKeQqg5FjAVVokF0/+B/87tYqTE22kkIJoOfFcT7GgOAu0Kw7zvex4zRPAP8zcKV3/NUGMPtJer2jRY+AgW3g4rqjKXvA5Q4w/GTIpuxKfppCoQC8n9E35YChW6BvrdVbex+nD0CGukrdAAeHwHiRstc93t3b3du/Z9r9/QCBanKsYNj7rgAAAAZiS0dEAAEAAQABsubIbgAAAAlwSFlzAAALEwAACxMBAJqcGAAAAAd0SU1FB+kBBQw3M9HsAS4AABZVSURBVHja7V15kGVldf+d7/vu9pbu18tMDwM4CCKIiKClxGCiA26AomJco2U0cRc1VUkl+TN/xKr8pahxAyWFKZNoVaoEBAtFAcEowUQtN1BngQG66f0td/uWkz/ufW+66WGmZ5gZuvvd0/WqXt133+t7z/19Z//OIVS0in741/c/X5D4j9zmu3df+0fTFUcOkqhYcJDu+Ni9YejXvn7qxJnn1v3Gt2/90PeDiisVWA5JngyuHK2Nn6eEwlhj2wvqQf2KiisVWA5JBPxl6EUAgMCLAIi3Vlw5SGoYb/qGt984UgsaviAp3nzD6+cAuPKj50pSYDAECRBQ63/nxnd+fSxQkQQY1ll+x41vnh/CxbR16a5P3Hc6gH8gEi8JvejZQsiaIIInfQAMAsE4h9ykYDDnOqVnbj8X1hkQEfbP/g6+FxoCqdCLQESD7+VGg+FgrGlnJvkDM98D4NqXffrFf6gky+akGQD/5Eu/ycx1a3XLAMhNBoABhpBCTRurfSLxTaWCZ7aTxQIUzFDSh3P2QWP1nxORtVbvAPWlEAEgKOnNKfZSbbIOQLOVZNnCdNs1P/DGgsnHd44/owUQuukyxurbsNidQ7M2CucsHlnY92icx7te8dk/NpWBO8Tkk3/LVOu0liQFQQJSKCzFcyABCAhI4eGU1uk7pcBnKm9omKXKh+541kRj6gWBF4DBAIDQi7DYncdoNF4eY/heiFZt8qqb3v+dbRVYhlWqeNEFI9FYyzk3OCaFwqnju+D44DEwUA9HJkdrYxdWYBlSagSNF0op1xj5Uqw+xGD40g88FTy7AsuQkhTeKaJkAZE40rmQwttVgWUY7ZUPf19IIU8DCNrmWOw9fkS/UZCYrMAyjFKFpCASYSFVCJIUwHwEvFBYgWUIybJ1YLYAIEjAVwFATx52IiYIQWcNM1jUsN64J/2QhNgFAgRkmTg8InElWYaQmBmChFx3CJsAgjinAssQEhH5RGLH0WQ8iOBXYBnSeyes/+ETEQiiVoFlKJFCvnEGqU6Q5vERX0kewziDOz/xI1mBZfgUkSyNl4HtysxgZ8HOgZ1b7UozQ0kPzPTCyhsathsX3otqfhOKJNp5G90RCV1XSBsKIAIB8DsaQdeg3rEYDVoIVAAArQoswyZXhIi6aRv7n9/A8nlngAggB8DxKquGReEvj/7iMZz1Ww0AzwNwewWWYXKdnX3h9OkeOudsh9Tu0Cc5HvhKnfN24NHZvQg7cmhD/sMbZ3EcpNsi0DrDbMRAvLMJMJ9TgWXYSNJuWzu6sAkX3NpegWWI6Hsfv/e55PgcpdcfvWcCyLgMAH3vo/c8ZyjtvK18c998700iUJ5XD0bqvgonpJBNx+5dvgquIZDUuyaw76IWXFB4QIfMOhe7PyAyg6l7HtobTHdGI6+mUpN+mdl9k8FL2mTznWSpa5zRb/rKVa4Cyyahr7/7G43RaPxCJf0LmtHIuZFXP0cKdbonvWd4yq/nJkcjaOKx9gGMReNo1xymd0h0tnkwkyOrMoXkHLz5HuozMcYPpBjNPLRdF6dEp8A4DQAwVqfG6X3a5g+lefJAN11+UFv9c23yX73h+isWthRY7vr4fe8E+G0M2HV+Zz/D7V+NONoIuLsQRK9uBI16IxwNIr8uiQTEEyrgjNWwzmLazKF99jjOf7goUdFs0M6XV+toEohkhCxNMFpr4RdnadR+P4tdZhLS8yGFfILz5MDMSHTXduKlLM57hpm/AeBBHNz1eEKXPnNRZL7ysgh0IUCtp/CzCsC3VD4efa0+uR2nL0fry78zrznPsYVz9mlDCYPhSb/YaUhURGL50M9GSQ+5yUAMdHfWYR/WUKTgk4/t0dSa++qkbYw1J5HbHMm2Gmq/ByxbEBxUGXnoc0SQAAhoBmNyNBqvMYBcJ39lnD0pS4kBeNJbs3CJigV9LPUVggj7w2V0dfcK5SSBlYQgCV7Pzx3iriUksAEyJo7dEStOGCiARAA5Xvv9FZTrDIEKwVzEW/rnW2cAI5DaHpT0UA9HwKVUKf6Hg3V9cPpQGySbdCyAFUIAQsD5EkpYhsjN+oCyRYw0Yw1YEKTmUoU+yblEa6rnuNzrHPk1ALWifrc7C0/6qAWNrckzY0HWOtHsAqd16xvq4hxbdNNlLMeLxSo+Ab9PBEjtDldJeSiRVDpMB+0CT/po1ScOgusYyTqLNO8hN+nGehbO4bSsheZMsiwE0R9ogzlF2hqEXh2t2jgynRx/3c7u4B3z4W2hgzE8CZnaQsk4t0qoMz81qUwgJHkXSvrQVj+t9t8h7TwhQUQsALQ3YmmpYwsHBz4Bl8YAVOZwxm+Tw+4XkkIhzePCZiHCmb9JEXRyOMHH+XqKfjBK+VDC26gmAYmVS2SDuMDwZQBtMnTiJdTD5nH/fSEkJAhTGD+szSKFQj0cRSddQjfroKkVpBMQQp2AJyHQTZeR2wxCPP0WMYFKtVp4l2C0FQMMEHKTQpu82LpZ6l5PelDSX+EynxzEEwGNqAU6hIdyPMgjhQXP4GE5jynbOKytIYgwWpsA2GFfuAzdZYgTsKBqfgPO2fV7pccRFCibFBmroW1ehB+cK66DGbWgCQAPKoB/7thdxMwI/dpBVDOQ5D20k0UQCPWwCSX8p2zIHY1dcaJYpkiiNx4gnQowtXd91wLn0D69Aa+7BGnUCbhfBpE4aUDphwMymyLRPYCLPnqhKrbEsCqA1E2X+8KDykO0Gmmlj9kIR9CgUYAZiY4R591SPMtCHgkBJbwSQAJSiONi8J1oEkKtibEcjestxEbPktDAy7Ou2EvHAKzTKDSKg3MOgghSeBirbVsTzKS18RmhmJ0yTsM4g8CLIIQciKCVQa5AhQhVBAYPVj2jrFkFYE2G3GYACLWgDl8GJ0SFHBfJojx4Cz2IbduOWqGwIEjeuDXbggQyk6CX9UBE8KUHJQMARduzvmggEmVUl9cEMwkEEsXnli1yk4Hh9qo0T767Z+Y3CRGMIHVpPRypjUStUV/6IZGQQkhJJVJ5YFOs0NpSlvZNgJpogJnRTdvopEuYaExhZe+TDbPuSIAsQ7jSNVonYsgB/mIKjG1MyUJEWE4WIElhvLGtdPOBJ0tL9ZsVFdKG4dg655zJTRK34+XlWHe7zrn7mJ2WQt2qXn7txTcCuLH/Aze977aJx6U6PfCiHTW/fraS/nOUVGcr4Z8nhNjpSR+BCuGroNCx7EoJwnClaG+GLcx3ppFmMUgIBCoaJLl4A0gbEgLkHGRqCk28TtHuZxYyM5Cl6n36lY0AiaK0ItVJET8igVrQWBOrEWVS1bKFNjmy0qFxbB/SVv/SObMvt/kDSdb5XarTh40zD7/huitWZVbXcOqq6y6fB9Dv8fodALjjmnslgACgwJPeS0iI9xBwReTXazW/wY1wlMQKe4XhIESxfzjVMRa6M2AwAhWhEY6UquwIEbETSFKowuYy9mieDLx2Cvc02yuF91K8j7M20jyBA9AIG/BVAKfdIC7Ud0Scc1hOF7ibtinTyQKAW5xz39Q2vwegDMzZZZ+75IireF3L6rLPXmIBxOXr1vKFmz5w60Sg6hcT0UX1oHlO5NV2+So8U0k1apxuaqfhqQBR0Ch7x2bopstwDCgpQURgBpRQA59eFtHCE2ooUxEmRq1tsN54P4GAk2C4r7x3W0oHZgtTvndsi+sgQqBCNKMWnHMwVsM4i9ykMM70rNXzqU72ZCbd00vbexn08zhr3/nG61/beUp8Ox50ywdul1IqX0B4gR+eShDPEkJe4Cv/xUr4l0oSjSioI/LLFeDsoP7DOA3nDBwwOC5KcaqEAjMfd2N578KDqLUmMSXG1/2d6XQGutvG6ZNnr/kszrqoh81jADhBlAlL5yx6WaeItwgJKVXhfUEUxilRoU6EgDYZelkXqU5gnZnVNr9Xm/yHzO63jvmhRMf7iVnHupdfff3r3HFbZCeDbvngdydqQXO3IHqTkt4ZNa8xFfnRlK+impQKkmQZeudyBTnEWQfGGSjhIVAhSBS2fFF0dOyX7pzFdOcRqDDC9mD9DSgfXz4AKRRGaxNQ0jtmsHCZXypcWIvMZODBAmkOCrYIBMcOlg2ss8h1miZ5fKCbt2fZ2d9Y577dyzt3vu6Lr1o4OSrwaaLvfOTObQQx5Stv0lfhpYLE+Up65wdetDOQQd1TAaRQ8JVfimQDxw7GGWibg7iotVDSB5ghhYJSHsBHjvomOoazGpnJMN5Yf7H+bPtRhF4ETwUIvdphwFJKCxTlEJZNeQ8argyGKemXKreIVREBuclhbA5jNTKbxZlOHtZWP+Cc/W9t8zu1yXuO7aOv+fzup2VuwIbzAW/70Pd3gfCMut/8U6W8i5TwLg5U0Az9ehR5db8vhkGAtQbaacAxDJuynIEG3tqTecXteBG1sIlMx6gHI+tm1VI8j5GohTjroBGOrgZL3kXNb4LByE0CbfIyACjK5CDgqwBSyMJVdQ6WLZK8l2UmSTOdto0zP81Nelec9X5MwMzlX7h070Z6NpuiYPt7H71nlxTyHCHEM3wZXqCU/zIl1QWBChF6taLFVxls4mJqB4zLB7ZOP1oZenUIIbAcL6IZjSLOumiEI+tiExGw2JtDqzaBTrqMZjgK6ywyEwNc1KN4qrArlPAGke1+ZZ42GVIdIzMZLJv7c53dl5n0p8y8xzmz5xWf+5OHNvpz2LTV/Xdccy8xcBERriAS5zf85nMCPzorUGHdkwE85UOtyA47dkiyLgxbGJtjJBpHkveOABaCsXlZc0vopEuYbO7AQmcWQgr40kcUNCBWbL8yTiM3ObTNoE0eJzp+oJd19zC7HzO7m5n5wVd+7k82ZVniltkKcv3bvuRPNE7bVgvqp9X85vlSqud50jvTk/5zPOU/q1/QLYVEbjJ40keSx4cFi7Y5tMnhe4XkaseLmGhuR5x1EXgRrDPQJkNuc2uteTAz2QPG6QPG6p/30qX7U53N/uzBe6f/8e5P2q3A4y29yez2j96tfOnXQBQKEucR6NUMvKURNs+cbOxAfATJkpsMVM4nIhAW4zm0auN4bOkhpHnyUwbuAvjfmN0jziHJTJy85vO7t+zkkKEcIfOt9922q1Wf+M/to6de3HyCobpKspgMWAmW3izmOjN3t+OFt1113eWPDRvfhnKv8+uvu3y/tvmfGZuvWwowGMYZWGeuGEagDC1Yysc/Z53bc7hio0EZRvnnnPnfyz57SW9YOTa0YDHW5MzuwOFymUp6MEYj0ykyncA4+zCGmIYWLK/5/G5nnZ0+XOa7n58KvajowE20XIFlWBURuy6v+1yGc+ZnFViGlBy7+fVmidlZgJFXYBlSsqwf43WW7lm21rJdqsAypJSbfJrB64iuEqyzxjk7V4FlSCnNe3NFK+0jQQVwzurcZrMVWIZVDVm7pJ3OjxjIJsA4Y7pJ+/EKLMPqDYEWU91LxBHqcBmAcXpJOxNXYBlWm0X35tI8jVcXbTPibHVNMzGQm3zO2jStwDKkdPVXX9/NdJyt3OhuncNib34VfhiMTCcH3vqvb64kyzBTZrLplTW7XBZIrw6/MHKTLg07r4YeLADdl+p40BDAskMjGEGmD2ocbTW0yX9dgWXIicE/yc1Bh8jYHPWwCePy0hEi5DoFIPZXYBlyMlbfneoYfbQ4doi8BvKyOh8EZCaFZX1/BZYhp8s/v3vWWD0wcZ2z5fBMGkiWVKe44guX7anAUhGY+aF+14H+rkgSK/ccm3bFpQosfbvlV67s7EBEcMWAcAAMyxYA31lxqQJLX3rc5wpQlNtOy87bPBjesLfiUgWW0k4xv2ZmgMpeSINmgIUNY9n+uOJSBZZSDWHZOQtmrNpd2PeOrNX7Ki5VYCkBYZets2l/t3SfiAjGag1QXHGpAgsAwLLrWrbJEysViADj9ALDLVVcqsACAMh1uuic7RTG7iq4wFjdc84lFZcqsAAAkqzbc+wKNbRq0DPBOL0w351ZrLhUgQUAYNhoa/VSv9K/36oVAKy16VtuuNpUXKrAAgC4+vrX9YzT0yBGL+0gyxO04yWUsZfjXkp51yf+Z1M2JNiUF337h+9WUVj7iSQ1Ahx1G0sikj8hgnbOgsuW5L4Mdp8ydtoZvayNZjiOdrKIml/Ho4v79zG77wshwczbmN25ONYGvkSeNrrxsk+/aFNOnVeb8aJ/tu9H4pXPv/rcbSM7a8c0RYP57CLgdlDdUDl6SUACYBAVc4l2tnadAeC9QsrjsDIJBxb27NusEnhTgkWzZgCajlU4UhFREVI8EURlLujg45Xy+LKIN/Hc0s1ps/RH5rIt1utxmn/EWDnN7fg91X7JQz//VEmWk0i+9AlESpsc2vbgSQ/RIYYbbASSJNHLOtBWw1d+5Q2dbPrbW/9eW2v3BF6ERtgEQJhvz8DY/Pi1beenJq0cO2iTY7Y7DSkkGtFo0bZjE3dmU5v0upnhkv70i8CLEPoRtMmQ6QTOWSjlw5MBlJBHDSDCE8b5rmfVkRh0/9Ymh5IKUniYaExtiLE5wwwW0KppfUXhkpIFQEBAplMkWRcWFiNha91mSN+7Yjoa24KxHC+ASMJXHprRKFAOfFoLFKYKLCeZ0jy9a74zc2EzanmDIZDlHxjwy5bsjh3ivAsuq98iv75CFfAaQ7SwcumwwOrjLsl7gwkm9XC0LJyiVcMe+tNNk7yHbrqcGau/UYHlZLvPNv+7xd7cpxZ7s9fUw9G/GI3GdtSD5mDuX/9pCxKo+cW8I+cslpPFonkySYR+tGaKBx/Bq8l0Cm1zWKvRjFqHGK3Lg3PZOXSzDpaS+UfirPNVAJ8iYNNmsLdEH9x/f/c3GlOjp726FjQ/0ghHLqkHTd+XIfgQswEFCWibwzgLYzJI6UEJBa+cPtJOltCqT2CpO4eR2ng5rSODcxbGaXiymFbiSe8QthCVgIoRZ920k3V+kGTdGx5Z3HfLu7729k2fud5STZO/9f5bZTNsneKr4G98L7xmsrFDBF542BF7rhzCEOddjETj6GUdjNUnMd+dQSMcQTtZQiMYgZLeYA7QWiYWo+jSPMZcZ4Zzk33SuPxfHpr97fS7v/4e3ir83bIdtm/74B3jUnkfGqtve9tI1Dor9KKoGOXCh1QvkiQWevNwTmOiuQMzywdQ9+toRK3BxLVDWS7WGSR5HLeTxd8sx/P/leS9T1315cu3ZP3Llm/HftP7bh0ZrU8+L/DCt3jS/9hYfRKRVx+E9aVQyHSCpd4ccps9WguaOycaU5htP4ok73V8L2yO1yfhq6icZ1QMQe9lXSzH88hN/s+5SW9+fPnR/3vzDW/Y0uWXQ9W7/45P3KvIiesa0cgbp0Z2Niw7mu9Mt9vx4p3O2XcEfu2l9WDk9p1ju/Dw/B6kee/SXMc/FkJ9rRG1Xrlt5JQGATyz/Einl3VvvPQzL/n4MPFvKAc93PyB26dCP7pSCU/GWfeWK7/4iscA4Acf/1Er9OozZ2w/x//dY79MnLOnvvzaixcB4NsfvOPUWlB7rbUm6STtm9/4lSuHrnpODSNYXvelV80A+OpaY5dTIrpPQLzUOXs/gIHtceUXL3sEwJcwxFRVyq2gyz5zSZrB/HKvvwAiPPDyay9OK65UYHlScoR7lrJFgHFzxY3V9P9w6VyTc79ZqgAAAABJRU5ErkJggg=="
            def acIconBytes = Base64.getDecoder().decode(acIconString)
            stream = new FileOutputStream("${kmzDir}/images/ac.png")
            stream.write(acIconBytes)
            }
          }
        } else {
          kmlFile = new File(kmlFileName)
        }
        if (kmlFile == null) throw new FileException('Cannot write ' + kmlFileName)
        kmlPrintStream = new PrintStream(kmlFile)
        if (kmlPrintStream == null)  throw new FileException('Cannot open ' + kmlFileName)
      }

      def journey = 0
      def fltTime = Duration.ZERO
      def blkTime = Duration.ZERO
      def track = 0
      def fuel = 0.0
      def eastMost = -200.0
      def westMost = 200.0
      def southMost = 200.0
      def northMost = -200.0
      float tachStart
      float tachEnd
      def numFlights = 0
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

        if (information) {
          flight.printInformation()
          if (i < (numFiles -1)) println "-------------------"
        }

        if (dump) flight.dump()

        if (kml && (!kmlOmitLocals || !(flight.startsAtHome && flight.endsAtHome)) && flight.flightDuration != null && flight.flightDuration.getSeconds() > 300) {
          if (flight.northMost > northMost) { northMost = flight.northMost }
          if (flight.southMost < southMost) { southMost = flight.southMost }
          if (flight.eastMost > eastMost) { eastMost = flight.eastMost }
          if (flight.westMost < westMost) { westMost = flight.westMost }
          flights[numFlights++] = new Flight(flight, kmlStep, kmlSmoothening)
        }

        if (fuelUsed) println flight.integFuel
      }
      if (kml) {
        Flight.printKmlHeader(kmlPrintStream, kmlWidth, richKml, kmlIcons, kmlForGE, kmlDirArrowSize, eastMost, westMost, southMost, northMost, kmlScaleFactor)

        if (kmlTourDuration > 0) {
          Flight.printMovingIconHeader(kmlPrintStream)
          def n = 0
          def acIcon
          if (kmlDirArrows) {
            acIcon = "images/ac.png"
          } else {
            acIcon = "http://maps.google.com/mapfiles/kml/shapes/airports.png"
          }
          for (def i = 0; i < numFlights; i++) {
            n = flights[i].printMovingIcon(kmlPrintStream, n, acIcon, kmlDirArrowSize)
          }
          Flight.printTourHeader(kmlPrintStream)
          def samples = n
          n = 0
          for (def i = 0; i < numFlights; i++) {
            n = flights[i].printTour(kmlPrintStream, n, eastMost, westMost, southMost, northMost, samples, kmlScaleFactor, kmlZoomIn, kmlTourDuration)
          }
          Flight.printMovingIconFooter(kmlPrintStream)
        }

        def fields = new Fields()
        def arrows = []
        for (def i = 0; i < numFlights; i++) {
          def colorIndex = 16
          if (kmlColorScheme == 1) colorIndex = i % 16
          if (kmlColorScheme == 2) colorIndex = journey % 16
          colorIndex = deck[colorIndex]
          if (kmlColorScheme == 0 && mixColors) colorIndex = deck[0]
          flights[i].printTrack(kmlPrintStream, colorIndex, richKml, kmlIcons, kmlForGE, kmlDirArrows, kmzDir, i, kmlDirArrowSize, fields, arrows)
          if (flights[i].endsAtHome && !mixColors) journey++
          if (flights[i].endsAtHome && mixColors) journey += (rnd.nextInt(5) + 1)
        }
        Flight.printKmlFooter(kmlPrintStream, kmlIcons, kmlForGE, kmlDirArrows, kmlDirArrowSize, flights[0], fields, arrows)
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
        if (kml) {
          println "North Most Point: ${northMost}"
          println "South Most Point: ${southMost}"
          println "West Most Point: ${westMost}"
          println "East Most Point: ${eastMost}"
        }
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
  static double homeLat = 49.473
  static double homeLong = 8.51323
  def startsAtHome
  def endsAtHome
  def lastFltIndex
  def firstFltIndex
  def eastMost
  def westMost
  def northMost
  def southMost

  // Distance from one coordinate to another (NM)
  def static coordDist(lat1, long1, lat2, long2) {
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

  // Track from one coordinate to anaother (degrees)
  def static coordTrack(lat1, long1, lat2, long2) {
    double phi1 = Math.toRadians(lat1)
    double phi2 = Math.toRadians(lat2)
    double dLambda = Math.toRadians(long2 - long1)
    double y = Math.sin(dLambda) * Math.cos(phi2)
    double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLambda)
    double theta = Math.atan2(y, x)
    return (theta * 180/Math.PI + 360) % 360
  }

  // Resulting coordinate from an origin coordinate, a track and a distance (degrees and NM)
  def static trackCoord(lat1, long1, track, dist) {
    double phi1 = Math.toRadians(lat1)
    double lambda1 = Math.toRadians(long1)
    double brng = Math.toRadians(track)
    double phi2 = Math.asin(Math.sin(phi1) * Math.cos(dist/3440.1) + Math.cos(phi1) *  Math.sin(dist/3440.1) * Math.cos(brng))
    double lambda2 = lambda1 + Math.atan2(Math.sin(brng) * Math.sin(dist/3440.1) * Math.cos(phi1), Math.cos(dist/3440.1) - Math.sin(phi1) * Math.sin(phi2))
    double  lat2 = (phi2 * 180/Math.PI) % 180
    double  long2 = (lambda2 * 180/Math.PI) % 180
    return [lat2 , long2]
  }

  // Diff in degrees between two courses (signed result)
  def static courseDiff(trk1, trk2) {
    def trkDiff = trk2 - trk1
    if (trkDiff > 180.0) trkDiff = trkDiff - 360.0
    if (trkDiff < -180.0) trkDiff = trkDiff + 360.0
    if (trkDiff == -180) trkDiff = 180
    return trkDiff
  }

  // Add correction angle to a course in degrees (cor may be negative)
  def static courseSum(trk, cor) {
    def trkSum = trk + cor
    if (trkSum >= 360.0) trkSum = trkSum - 360.0
    if (trkSum < 0) trkSum = 360.0 + trkSum
    return trkSum
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

  def Flight (Flight f, stepSeconds, smoothRange) {
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
    eastMost = f.eastMost
    westMost = f.westMost
    northMost = f.northMost
    southMost = f.southMost

    lastFltIndex = -1
    firstFltIndex = -1

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
    for (i = 0; i < data.size() && firstFltIndex == -1; ++i) {
      if (data[i].flt_tm != 0.0) {
        for (j = i; j < data.size() && firstFltIndex == -1; ++j) {
          if (data[j].gps_speed >= 20 && data[i].gps_long != Double.NaN && data[i].gps_lat != Double.NaN) firstFltIndex = j
        }
      }
    }
    for (i = data.size() - 1; i >= 0 && lastFltIndex == -1; --i) {
        if (data[i].flt_tm != 0.0 && data[i].gps_long != Double.NaN && data[i].gps_lat != Double.NaN) lastFltIndex = i
    }
    if (firstFltIndex != -1 && lastFltIndex != -1 && smoothRange > 1) {
      for (i = firstFltIndex; i < lastFltIndex; i++) {
        def trk1 = coordTrack(data[i].gps_lat, data[i].gps_long, data[i + 1].gps_lat, data[i + 1].gps_long)
        def trkCor = 0.0
        for (j = 2; j <= smoothRange && (i + j) < lastFltIndex; j++) {
          def trkN = coordTrack(data[i].gps_lat, data[i].gps_long, data[i + j].gps_lat, data[i + j].gps_long)
          def trkDiff = courseDiff(trk1, trkN)
          trkCor += trkDiff / smoothRange
        }
        def trk = courseSum(trk1, trkCor)
        def dist = coordDist(data[i].gps_lat, data[i].gps_long, data[i + 1].gps_lat, data[i + 1].gps_long)
        def ret = trackCoord(data[i].gps_lat, data[i].gps_long, trk, dist)
        data[i].calc_track = trk
        data[i + 1].gps_lat = ret[0]
        data[i + 1].gps_long = ret[1]
      }
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

    eastMost = -200.0
    westMost = 200.0
    southMost = 200.0
    northMost = -200.0

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
                 coordDist(set.gps_lat, set.gps_long, firstLat, firstLong) > 0.010799) {
        offBlock = set.timeStamp
        offBlockLat = set.gps_lat
        offBlockLong = set.gps_long
        offBlockAlt = set.gps_alt
        ofbIndex = i
      }
      if (firstWithGpsTime != null && set.gps_lat != Double.NaN && set.gps_lat > northMost) { northMost = set.gps_lat }
      if (firstWithGpsTime != null && set.gps_lat != Double.NaN && set.gps_lat < southMost) { southMost = set.gps_lat }
      if (firstWithGpsTime != null && set.gps_long != Double.NaN && set.gps_long > eastMost) { eastMost = set.gps_long }
      if (firstWithGpsTime != null && set.gps_long != Double.NaN && set.gps_long < westMost) { westMost = set.gps_long }
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
                        coordDist(data[i].gps_lat, data[i].gps_long, lastLat, lastLong) > 0.010799) {
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

    firstFltIndex = -1
    lastFltIndex = -1
    for (def i = 0; i < data.size() && firstFltIndex == -1; ++i) {
      if (data[i].flt_tm != 0.0) {
        for (def j = i; j < data.size() && firstFltIndex == -1; ++j) {
          if (data[j].gps_speed >= 20 && data[i].gps_long != Double.NaN && data[i].gps_lat != Double.NaN) firstFltIndex = j
        }
      }
    }

    if (firstFltIndex != -1) {
      for (int i = data.size() - 1; i >= 0 && lastFltIndex == -1; --i) {
        if (data[i].flt_tm != 0.0 && data[i].gps_long != Double.NaN && data[i].gps_lat != Double.NaN) lastFltIndex = i
      }
    }

   if (firstFltIndex != -1 && lastFltIndex != -1) {
      takeOffTime = data[firstFltIndex].timeStamp
      landingTime = data[lastFltIndex].timeStamp
      flightDuration = Duration.between(takeOffTime, landingTime)
    }

    avgSpeed = 0.0
    maxAlt = -100000
    if (flightDuration != null) {
      for (def i = firstFltIndex ; i <= lastFltIndex ; i++) {
        avgSpeed = avgSpeed + data[i].gps_speed
        maxAlt = data[i].gps_alt > maxAlt ? data[i].gps_alt : maxAlt
        if (Double.NaN == data[i].gps_lat) {
          def j
          for (j = i + 1 ; Double.NaN == data[j].gps_lat ; j++) ;
          def inc = (data[j].gps_lat - data[i - 1].gps_lat) / (j - i + 1.0)
          for (def k = i ; k < j; k++) {
            data[k].gps_lat = data[k - 1].gps_lat + inc
          }
        }
        if (Double.NaN == data[i].gps_long) {
          def j
          for (j = i + 1 ; Double.NaN == data[j].gps_long ; j++) ;
          def inc = (data[j].gps_long - data[i - 1].gps_long) / (j - i + 1.0)
          for (def k = i ; k < j; k++) {
            data[k].gps_long = data[k - 1].gps_long + inc
          }
        }
        if (Double.NaN == data[i].gps_alt) {
          def j
          for (j = i + 1 ; Double.NaN == data[j].gps_alt ; j++) ;
          def inc = (data[j].gps_alt - data[i - 1].gps_alt) / (j - i + 1.0)
          for (def k = i ; k < j; k++) {
            data[k].gps_alt = data[k - 1].gps_alt + inc
          }
        }
        def j
        for (j = i + 1; coordDist(data[i].gps_lat, data[i].gps_long, data[j].gps_lat, data[j].gps_long) < 0.001; j++) ;
        for (def k = i + 1; k < j; k++) {
          data[k].gps_lat = data[k-1].gps_lat + (data[j].gps_lat - data[i].gps_lat) / (j - i)
          data[k].gps_long = data[k-1].gps_long + (data[j].gps_long - data[i].gps_long) / (j - i)
        }
      }
      avgSpeed = avgSpeed / (lastFltIndex - firstFltIndex + 1)

      track = Math.round(avgSpeed * (flightDuration.toMillis() / 3600000))
    } else {
      avgSpeed = 0
      track = 0
    }

    loggingDuration = Duration.between(this.fltStart, data[data.size() - 1].timeStamp)

   if (firstFltIndex != -1 && lastFltIndex != -1) {
      takeOffLat = data[firstFltIndex].gps_lat
      takeOffLong = data[firstFltIndex].gps_long
      takeOffAlt = data[firstFltIndex].gps_alt
      landingLat = data[lastFltIndex].gps_lat
      landingLong = data[lastFltIndex].gps_long
      landingAlt = data[lastFltIndex].gps_alt
    }

    if (takeOffLat != null) {
      dist = coordDist(takeOffLat, takeOffLong, landingLat, LandingLong)
      startsAtHome = (coordDist(takeOffLat, takeOffLong, homeLat, homeLong) < 3.0)
    } else {
      dist = 0
      startsAtHome = false
    }
    if (landingLat != null) {
      endsAtHome = (coordDist(landingLat, landingLong, homeLat, homeLong) < 3.0)
    } else {
      dist = 0
      endsAtHome = false
    }

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

  def static lColors_w = ['ff7e649e', 'ffa7a700', 'ffb18ff3', 'ffb0279c', 'ff007cf5', 'ff7b18d2', 'ffd18802', 'ff177781',
                          'ff00d6ef', 'ffb73a67', 'ffda8a9f', 'ff0051c6', 'ff2f8b55', 'ff444444', 'ff4242ff', 'ff8dffff', 'ffbf85c2']
  def static lColors_e = ['fff01cce', 'ffa7a700', 'ffb18ff3', 'ffd18802', 'ffb0279c', 'ff007cf5', 'ff5b18c2', 'ff74b7e9',
                          'ffea4882', 'ff00c6df', 'ffda8a9f', 'ff0051c6', 'ff69b355', 'ffaaaaaa', 'ff4242ff', 'ff8dffff', 'ffbf85c2']
  def static lColors
  def static iconScale
  def static iColor1 = '880E4F'
  def static iColor1r = 'ff4f0e88'
  def static iColor2 = '01579B'
  def static iColor2r = 'ff9b5701'

  def static printKmlHeader (file, width, rich, icons, forGE, arrowSize, maxLong, minLong, minLat, maxLat, rangeFactor) {
  def centerLong = (minLong + maxLong) / 2.0
  def centerLat =(minLat + maxLat) / 2.0
  def northSouth = coordDist(maxLat, centerLong, minLat, centerLong)
  def eastWest = coordDist(centerLat, minLong, centerLat, maxLong)
  def range = Math.sqrt(eastWest * eastWest + northSouth * northSouth) * rangeFactor
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
    file.println '<?xml version="1.0" encoding="UTF-8"?>'
    file.println '<kml xmlns="http://www.opengis.net/kml/2.2"'
    file.println ' xmlns:gx="http://www.google.com/kml/ext/2.2">'
    file.println '<Document>'
    file.println '    <LookAt>'
    file.println "      <longitude>${centerLong}</longitude>"
    file.println "      <latitude>${centerLat}</latitude>"
    file.println '      <altitude>0</altitude>'
    file.println "      <range>${range}</range>"
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
      file.println '      	<Icon>'
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

  def static printKmlFooter (file, icons, forGE, arrows, arrowSize, firstFlight, fields, dirArrows) {
    if (icons) {
      file.println '  </Folder>'
      file.println '  <Folder>'
      file.println '    <name>Aerodromes</name>'

      if (fields.hasHome) {
        file.println '    <Placemark>'
        file.println '     <name>Home</name>'
        file.println "     <styleUrl>#icon-1591-${iColor1}-nodesc</styleUrl>"
        file.println '     <Point>'
        file.println '      <altitudeMode>absolute</altitudeMode>'
        file.println "      <gx:drawOrder>2</gx:drawOrder>"
        file.println '      <coordinates>'
        file.println "      ${homeLong},${homeLat}"
        file.println '      </coordinates>'
        file.println '     </Point>'
        file.println '    </Placemark>'
      }

      for (def i = 0; i < fields.fields.size(); i++) {
        file.println '    <Placemark>'
        if (!forGE) {
          file.println '     <name>AD</name>'
          file.println "     <styleUrl>#icon-1750-${iColor2}-nodesc</styleUrl>"
        } else {
          file.println "     <Style>"
          file.println "        <IconStyle>"
          file.println "        <heading>${fields.fields[i].landingTrack}</heading>"
          file.println "          <scale>${iconScale}</scale>"
          file.println "          <Icon>"
          if (forGE && arrows) {
            file.println '          <href>images/ad.png</href>'
          } else {
            file.println '          <href>http://maps.google.com/mapfiles/kml/shapes/airports.png</href>'
          }
          file.println "          </Icon>"
          file.println "        </IconStyle>"
          file.println "     </Style>"
        }
        file.println '     <Point>'
        file.println "      <gx:drawOrder>2</gx:drawOrder>"
        file.println '      <altitudeMode>absolute</altitudeMode>'
        file.println '      <coordinates>'
        file.println "        ${fields.fields[i].lon},${fields.fields[i].lat},${fields.fields[i].alt * (12 * 0.0254) + 10}"
        file.println '      </coordinates>'
        file.println '     </Point>'
        file.println '    </Placemark>'
      }
    }
    file.println '  </Folder>'
    if (arrows) {
      file.println '  <Folder>'
      file.println '    <name>Arrows</name>'
      dirArrows.each { a ->
        file.println '    <Placemark>'
        file.println "     <Style>"
        file.println "        <IconStyle>"
        file.println "        <color>${a.color}</color>"
        if (forGE) {
          file.println "        <heading>${a.track}</heading>"
        }
        file.println "          <scale>${iconScale}</scale>"
        file.println "          <Icon>"
        file.println "            <href>images/${a.icon}</href>"
        file.println "          </Icon>"
        file.println "          <hotSpot x=\"${a.xHot}\" y=\"${a.yHot}\" xunits=\"fraction\" yunits=\"fraction\"/>"
        file.println "        </IconStyle>"
        file.println "     </Style>"
        file.println '     <Point>'
        file.println "       <gx:drawOrder>1</gx:drawOrder>"
        file.println '       <altitudeMode>absolute</altitudeMode>'
        file.println '       <coordinates>'
        file.println "         ${a.lon},${a.lat},${a.alt * (12 * 0.0254)}"
        file.println '       </coordinates>'
        file.println '     </Point>'
        file.println '    </Placemark>'
      }
    }
    file.println '  </Folder>'
    file.println '</Document>'
    file.println '</kml>'
  }

  def static printMovingIconHeader (file) {
      file.println '    <name>Flight path with timestamps</name>'
      file.println '    <open>1</open>'
      file.println '     <Style>'
      file.println '       <ListStyle>'
      file.println '         <listItemType>checkHideChildren</listItemType>'
      file.println '        </ListStyle>'
      file.println '     </Style>'
  }

  def static printTourHeader (file) {
    file.println '  </Folder>'
    file.println '    <gx:Tour>'
    file.println '      <name>Play me</name>'
    file.println '      <gx:Playlist>'
  }

  def static  printMovingIconFooter (file) {
    file.println '      </gx:Playlist>'
    file.println '    </gx:Tour>'
    file.println '  <Folder>'
  }

  def printMovingIcon (file, start, icon, iconSize) {
      if (takeOffTime == null || landingTime == null) return start
      def startTime = OffsetDateTime.of(2024, 1, 1, 0, 1, 5, 0, ZoneOffset.UTC)
      long marks = start
      def scale =  iconSize / 2.0 - 1
      for (def i = firstFltIndex; i < lastFltIndex; ++i) {
        if (!Double.isNaN(data[i].gps_lat) && !Double.isNaN(data[i].gps_long)) {
          def i1 = i + 1
          while (Double.isNaN(data[i1].gps_lat) || Double.isNaN(data[i1].gps_long)) i1++
          def i2 = i1 + 1
          while (Double.isNaN(data[i2].gps_lat) || Double.isNaN(data[i2].gps_long)) i2++
          def track1 = coordTrack(data[i].gps_lat, data[i].gps_long, data[i1].gps_lat, data[i1].gps_long)
          def track2 = coordTrack(data[i].gps_lat, data[i].gps_long, data[i2].gps_lat, data[i2].gps_long)
          def track = (track1 + track2) * 0.5
          file.println '    <Placemark>'
          file.println '      <gx:TimeSpan>'
          def begin = startTime.plusSeconds(marks * 60 - 30).toInstant()
          def end = startTime.plusSeconds(marks * 60 + 30).toInstant()
          file.println "        <begin>${begin}</begin>"
          file.println "        <end>${end}</end>"
          file.println '      </gx:TimeSpan>'
          file.println "     <Style>"
          file.println "        <IconStyle>"
          file.println "        <heading>${data[i].calc_track}</heading>"
          file.println "          <scale>${scale * 1.5}</scale>"
          file.println "          <Icon>"
          file.println "            <href>${icon}</href>"
          file.println "          </Icon>"
          file.println "          <hotSpot x=\"0.5\" y=\"0.7\" xunits=\"fraction\" yunits=\"fraction\"/>"
          file.println "        </IconStyle>"
          file.println "     </Style>"
          file.println '      <Point>'
          file.println "        <gx:drawOrder>3</gx:drawOrder>"
          file.println '        <altitudeMode>absolute</altitudeMode>'
          file.println "        <coordinates>${data[i].gps_long},${data[i].gps_lat},${data[i].gps_alt * (12 * 0.0254) + 30}</coordinates>"
          file.println '      </Point>'
          file.println '    </Placemark>'
          marks += 1
        }
      }
      return marks
  }

  def printTour (file, start, maxLong, minLong, minLat, maxLat, points, rangeFactor, zoomIn, tourDuration) {
      if (takeOffTime == null || landingTime == null) return start
      def lookDuration = tourDuration * (1.0 / points)
      def centerLong = (minLong + maxLong) / 2.0
      def centerLat =(minLat + maxLat) / 2.0
      def northSouth = coordDist(maxLat, centerLong, minLat, centerLong)
      def eastWest = coordDist(centerLat, minLong, centerLat, maxLong)
      def range1 = Math.sqrt(eastWest * eastWest + northSouth * northSouth) * rangeFactor
      def startTime = OffsetDateTime.of(2024, 1, 1, 0, 1, 5, 0, ZoneOffset.UTC)
      long marks = start
      for (def i = firstFltIndex; i < lastFltIndex; ++i) {
        if (!Double.isNaN(data[i].gps_lat) && !Double.isNaN(data[i].gps_long)) {
          def range
          def lat
          def lon
          if (zoomIn) {
            def prog = new Double(marks) / new Double(points)
            def a1 = 2.0 * Math.abs(prog - 0.5)
            def a2 = 1.0 - a1
            range = range1 - (range1 * 0.8 * a2)
            lat = centerLat * a1 + data[i].gps_lat * a2
            lon = centerLong * a1 + data[i].gps_long * a2
          } else {
            range = range1
            lat = centerLat
            lon = centerLong
          }
          file.println '        <gx:FlyTo>'
          file.println "          <gx:duration>${lookDuration}</gx:duration>"
          file.println '          <gx:flyToMode>smooth</gx:flyToMode>'
          file.println '          <LookAt>'
          file.println '            <gx:TimeStamp>'
          def time = startTime.plusMinutes(marks).toInstant()
          file.println "              <when>${time}</when>"
          file.println '            </gx:TimeStamp>'
          file.println "            <longitude>${lon}</longitude>"
          file.println "            <latitude>${lat}</latitude>"
          file.println '            <altitude>0</altitude>'
          file.println "            <range>${range}</range>"
          file.println '          </LookAt>'
          file.println '        </gx:FlyTo>'
          marks += 1
        }
      }
      return marks
  }

  def printTrack (file, index, rich, icons, forGE, arrows, kmzDir, trackNo, arrowSize, fields, dirArrows) {
    if (trackNo == 0) {
      file.println "  <name>Tracks</name>"
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
        def track = data[position].calc_track
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
        dirArrows << new Arrow(lColors[index], track, icon, xHot, yHot, data[position].gps_lat, data[position].gps_long, data[position].gps_alt)
      }
    }
    if (icons && !startsAtHome && firstFltIndex != -1) {
      def ad = new Aerodrome(takeOffLat, takeOffLong, data[firstFltIndex].gps_alt, data[firstFltIndex].calc_track)
      fields.addField(ad)
    }
    if (icons && !endsAtHome && lastFltIndex != -1) {
      def ad = new Aerodrome(landingLat, landingLong, data[lastFltIndex].gps_alt, data[lastFltIndex - 1].calc_track)
      fields.addField(ad)
    }
    if (startsAtHome || endsAtHome) fields.hasHome = true
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
  double calc_track

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
          calc_track = gps_track
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
    calc_track = f.calc_track

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

class Aerodrome {
  def lat
  def lon
  def alt
  def landingTrack

  def Aerodrome (lt, ln, a, t) {
    lat = lt
    lon = ln
    alt = a
    landingTrack = t
  }
}

class Fields {
  def fields
  def hasHome

  def Fields() {
    hasHome = false
    fields = []
  }

  def addField(n) {
    for (def i = 0; i < fields.size(); i++) {
      if (Flight.coordDist(n.lat, n.lon, fields[i].lat, fields[i].lon) < 3.0) {
        return
      }
    }
    fields << n
  }
}

class Arrow {
  def color
  def track
  def icon
  def xHot
  def yHot
  def lat
  def lon
  def alt

  Arrow(c, t, i, x, y, lt, ln, a) {
    color = c
    track = t
    icon = i
    xHot = x
    yHot = y
    lat = lt
    lon = ln
    alt = a
  }
}
