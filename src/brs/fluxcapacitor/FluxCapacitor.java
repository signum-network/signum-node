package brs.fluxcapacitor;

//TODO: Create JavaDocs and remove this
@SuppressWarnings({ "checkstyle:MissingJavadocTypeCheck", "checkstyle:MissingJavadocMethodCheck" })

public interface FluxCapacitor {
    <T> T getValue(FluxValue<T> fluxValue);

    <T> T getValue(FluxValue<T> fluxValue, int height);

    Integer getStartingHeight(FluxEnable fluxEnable);
}
