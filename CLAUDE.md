@AGENTS.md

# This plugin

## "I NEED YOUR HELP!" list

`src/main/java/com/farmrunautopilot/ui/HelpWanted.java` lists, per sidebar tab (Run, Farm, Travel, Account), the features that couldn't be tried in development and need feedback from players. Keep it current without being asked:

- **Add** a line when you build or change a feature that the user can't test on their account (an item, unlock, quest or spellbook they don't have) or that relies on an UNVERIFIED game value: varbit, NPC/object ID, chat message wording, widget.
- **Remove** the line once the user confirms it works in game, and drop the matching UNVERIFIED comment in the code at the same time.
- **Reword** a line when the feature it describes changes.
- Put each line on the tab where the player would see the feature. Keep lines short and in plain language a player understands.
- Mention any change to the list when reporting back.
