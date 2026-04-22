import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.sql.SQLException;

public class SentenceBuilderTest {

    private SentenceBuilder builder;
    private StubDBMan dbMan;

    @BeforeEach
    void setUp() {
        dbMan = new StubDBMan();
        builder = new SentenceBuilder(dbMan);
    }

    @Test
    void testMemoryLoading() throws SQLException {
        // Setup data in stub
        dbMan.setStarters(Arrays.asList("The", "A"));
        
        Bigram b1 = new Bigram(); b1.word1 = "The"; b1.word2 = "cat";
        dbMan.setBigrams(Arrays.asList(b1));

        builder.loadDatabaseIntoMemory();
        
        // We can't directly check private fields, but we can verify behavior
        // through autocomplete/generation logic if we add getters or test methods.
    }

    @Test
    void testNextWordLogic() {
        // Testing the helper pickNextWord indirectly or directly if made accessible
        // For now, let's assume we want to verify the sentence generation logic
    }

    // A simple stub to avoid needing a real MySQL server for unit logic
    private static class StubDBMan extends DBMan {
        private List<String> starters = new ArrayList<>();
        private List<Bigram> bigrams = new ArrayList<>();

        public void setStarters(List<String> s) { this.starters = s; }
        public void setBigrams(List<Bigram> b) { this.bigrams = b; }

        @Override
        public List<String> getSentenceStarters(int limit) { return starters; }
        @Override
        public List<Bigram> loadBigrams(int limit) { return bigrams; }
        @Override
        public List<Trigram> loadTrigrams(int limit) { return new ArrayList<>(); }
        @Override
        public void connect() {} // Do nothing
        @Override
        public void disconnect() {}
    }
}
