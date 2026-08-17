package job_agent.service;

import java.util.List;

/**
 * DTO holding the structured result from a Gemini AI evaluation.
 * All fields correspond directly to the JSON keys returned by the model.
 */
public class EvaluationResult {

    private final int overallScore;
    private final int technicalMatch;
    private final int languageMatch;
    private final int levelMatch;
    private final int locationMatch;
    private final List<String> strengths;
    private final List<String> missingSkills;
    private final String summary;
    private final String recommendation;

    public EvaluationResult(int overallScore,
                            int technicalMatch,
                            int languageMatch,
                            int levelMatch,
                            int locationMatch,
                            List<String> strengths,
                            List<String> missingSkills,
                            String summary,
                            String recommendation) {
        this.overallScore = overallScore;
        this.technicalMatch = technicalMatch;
        this.languageMatch = languageMatch;
        this.levelMatch = levelMatch;
        this.locationMatch = locationMatch;
        this.strengths = strengths;
        this.missingSkills = missingSkills;
        this.summary = summary;
        this.recommendation = recommendation;
    }

    public int getOverallScore()    { return overallScore; }
    public int getTechnicalMatch()  { return technicalMatch; }
    public int getLanguageMatch()   { return languageMatch; }
    public int getLevelMatch()      { return levelMatch; }
    public int getLocationMatch()   { return locationMatch; }
    public List<String> getStrengths()      { return strengths; }
    public List<String> getMissingSkills()  { return missingSkills; }
    public String getSummary()              { return summary; }
    public String getRecommendation()       { return recommendation; }
}
