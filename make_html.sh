#!/bin/bash

transform_to_filename() {
    echo $1 | iconv -c -f utf-8 -t ascii | awk '{print tolower($0)}' | sed 's/ //g' | sed 's/\.//g'
}

make_template() {
    if [[ "$ONE_OR_MANY" == '1' ]]; then
        echo '<h4>###1, ###2</h4>'
    fi
    echo '<table>'
    echo '<tbody>'
    echo '<tr>'
    echo '<td>First name:</td>'
    echo '<td>###2</td>'
    echo '</tr>'
    echo '<tr>'
    echo '<td>Last name:</td>'
    echo '<td>###1</td>'
    echo '</tr>'
    echo '<tr>'
    echo '<td>E-Mail:</td>'
    echo '<td>###3</td>'
    echo '</tr>'
    echo '<tr>'
    echo '<td>Phone:</td>'
    echo '<td>###5</td>'
    echo '</tr>'
    echo '<tr>'
    echo '<td>Reg.:</td>'
    echo '<td>###6</td>'
    echo '</tr>'
    echo '<tr>'
    echo '<td>Make&amp;Model:</td>'
    echo '<td>###7</td>'
    echo '</tr>'
    echo '<tr>'
    echo '<td>Home base ICAO:&nbsp;&nbsp;</td>'
    echo '<td>###8</td>'
    echo '</tr>'
    echo '<tr>'
    echo '<td>Details: ###10</td>'
    echo '<td>---</td>'
    echo '</tr>'
    echo '</tbody>'
    echo '</table>'
    echo '<p>###images</p>'
    echo '###footer'
}


img_size() {
    #echo ${IMGDIR}/${1}
    if [[ -f "${IMGDIR}/${1}" ]]; then
        HIGHT=`identify  -format "%h" "${IMGDIR}/$1"`
        WIDTH=`identify  -format "%w" "${IMGDIR}/$1"`
        if [[ $HIGHT -gt $WIDTH ]]; then
            RET=PORTRAIT
        else
            RET=LANDSCAPE
        fi
    else
        RET=ABSENT
    fi
    echo $RET
}

if [[ $# -ne 4 ]]; then
    echo "Usage: $0 CSV_FILE OUTPUT IMAGE_DIR 1|m"
    exit 1
fi
echo "Slowest script in the World..."

IMGDIR="${3}"
ONE_OR_MANY="${4}"

TEMPLATE=`make_template`
IMG_TEMP='<img src="images/empoa/members/###file" ###alt ###size />'
LNNO=0

rm -rf "${2}"
if [[ "$ONE_OR_MANY" != '1' ]]; then
    mkdir "${2}"
fi

while read MEMBER ; do
    SURNAME1=`echo $MEMBER | cut -d ';' -f 1`
    NAME2=`echo $MEMBER | cut -d ';' -f 2`
    EMAIL3=`echo $MEMBER | cut -d ';' -f 3`
    REPLY4=`echo $MEMBER | cut -d ';' -f 4`
    TELEPHONE5=`echo $MEMBER | cut -d ';' -f 5`
    REGISTRATION6=`echo $MEMBER | cut -d ';' -f 6`
    TYPE7=`echo $MEMBER | cut -d ';' -f 7`
    HOMEBASE8=`echo $MEMBER | cut -d ';' -f 8`
    DETAILS10=`echo $MEMBER | cut -d ';' -f 10`
    if [[ $LNNO -gt 0 && "$REPLY4" == "x" ]] ; then
        echo -n "Processing member ${LNNO}, ${SURNAME1}, ${NAME2}"
        FILENAME_BASE=`transform_to_filename "${SURNAME1}-${NAME2}"`
        CUR_TEMP=`echo "$TEMPLATE" | sed s/###1/"$SURNAME1"/g`
        CUR_TEMP=`echo "$CUR_TEMP" | sed s/###2/"$NAME2"/g`
        CUR_TEMP=`echo "$CUR_TEMP" | sed s/###3/"$EMAIL3"/g`
        CUR_TEMP=`echo "$CUR_TEMP" | sed s/###5/"$TELEPHONE5"/g`
        CUR_TEMP=`echo "$CUR_TEMP" | sed s/###6/"$REGISTRATION6"/g`
        CUR_TEMP=`echo "$CUR_TEMP" | sed s/###7/"$TYPE7"/g`
        CUR_TEMP=`echo "$CUR_TEMP" | sed s/###8/"$HOMEBASE8"/g`
        CUR_TEMP=`echo "$CUR_TEMP" | sed s/###10/"$DETAILS10"/g`
        CREW1=`img_size "${FILENAME_BASE}_1.jpg"`
        CREW2=`img_size "${FILENAME_BASE}_2.jpg"`
        AIRCRAFT=`img_size "${FILENAME_BASE}_ac.jpg"`
        # landscape => width 150 else height 150
        IMAGELINE=""
        IMNO=0
        if [[ "$AIRCRAFT" != ABSENT ]]; then
            echo -n " Aircraft: $AIRCRAFT"
            if [[ "$AIRCRAFT" == LANDSCAPE ]]; then
                SIZE='width="150"'
            else
                SIZE='height="150"'
            fi
            let IMNO++
            IMAGELINE=`echo "$IMG_TEMP" | sed s/###file/${FILENAME_BASE}_ac.jpg/g | sed s/###size/$SIZE/g | sed s/###alt/alt=\"$REGISTRATION6\"/g`
        fi
        if [[ "$CREW1" != ABSENT ]]; then
            echo -n " Crew1: $CREW1"
            if [[ "$CREW1" == LANDSCAPE ]]; then
                SIZE='width="150"'
            else
                SIZE='height="150"'
            fi
            if [[ "$IMAGELINE" != "" ]]; then
                IMAGELINE="${IMAGELINE};"
            fi
            let IMNO++
            IMAGELINE=${IMAGELINE}`echo "$IMG_TEMP" | sed s/###file/${FILENAME_BASE}_1.jpg/g | sed s/###size/$SIZE/g | sed s/###alt/alt=\""$NAME2 $SURNAME1"\"/g`
        fi
        if [[ "$CREW2" != ABSENT ]]; then
            echo -n " Crew2: $CREW2"
            if [[ "$CREW2" == LANDSCAPE ]]; then
                SIZE='width="150"'
            else
                SIZE='height="150"'
            fi
            if [[ "$IMAGELINE" != "" ]]; then
                IMAGELINE="${IMAGELINE};"
            fi
            let IMNO++
            IMAGELINE=${IMAGELINE}`echo "$IMG_TEMP" | sed s/###file/${FILENAME_BASE}_2.jpg/g | sed s/###size/$SIZE/g | sed s/###alt/alt=\""$NAME2 $SURNAME1"\"/g`
        fi
        echo " - $IMNO image(s)"
        CUR_TEMP=`echo "$CUR_TEMP" | sed s%###images%"$IMAGELINE"%g`
        if [[ "$ONE_OR_MANY" == '1' ]]; then
            if [[ $IMNO -gt 0 ]]; then
                CUR_TEMP=`echo "$CUR_TEMP" | sed s/###footer/"\<p\>\&nbsp;\<\/p\>"/g`
            else
                CUR_TEMP=`echo "$CUR_TEMP" | sed s/###footer//g`
            fi                
            echo "$CUR_TEMP" >> "${2}"
        else
            CUR_TEMP=`echo "$CUR_TEMP" | sed s/###footer//g`
            echo "$CUR_TEMP" >"${2}/$FILENAME_BASE.htm"
        fi
    fi
    let LNNO++
done <$1
