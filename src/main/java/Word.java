/*
    Written by Owen Giles for CS4485.0W1, Sentence Builder, starting April 5th, 2026
        NetID: oag220003
    This class is a POJO (Plain Old Java Object) that is used to package
    information between the Database Manager layer and the
    UI/CorpusParser/Reporter layers.
*/
public class Word {
	public String word;
	public int totalCount;
	public int startCount;
	public int endCount;
	public int boostTotalCount;
	public int boostStartCount;
	public int effectiveTotalCount;
}
