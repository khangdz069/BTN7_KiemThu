package org.example.editvideoytbtool.library;

import java.util.ArrayList;
import java.util.List;

public class CharacterLibraryIndex {
    private int schemaVersion = 1;
    private List<CharacterAsset> characters = new ArrayList<>();

    public CharacterLibraryIndex() {
    }

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    public List<CharacterAsset> getCharacters() { return characters; }
    public void setCharacters(List<CharacterAsset> characters) { this.characters = characters == null ? new ArrayList<>() : new ArrayList<>(characters); }
}
