package investor;

public class Seat {
    public final String businessLabel; // "Business: X"
    public final long price;
    public final Property property;    // which property this gives entry to
    public int ownerIndex = -1;

    public Seat(String businessLabel, long price, Property property) {
        this.businessLabel = businessLabel;
        this.price = price;
        this.property = property;
    }

    public boolean isOwned() { return ownerIndex >= 0; }
}
