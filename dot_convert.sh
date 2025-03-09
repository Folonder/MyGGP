# for f in $(find . -name '*.dot'); do dot -Tpng $f -o $f.png; done
# for f in $(find . -name '*.dot'); do DIR="$(dirname "${VAR}")"; FILE="$(basename "${VAR}")"; cd $DIR; do echo $FILE; cd -; done
for f in $(find . -name '*.dot'); do
    DIR="$(dirname $f)";
    FILE="$(basename $f)"
    cd $DIR
    dot -Tsvg $FILE -o $FILE.svg
    cd -
done
