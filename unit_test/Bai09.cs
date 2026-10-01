using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace N7_btn
{
    [TestClass]
    public class Bai09
    {
        public TestContext TestContext { get; set; }

        [TestMethod]
        [DeploymentItem("data_csv\\Bai09.csv")]
        [DataSource("Microsoft.VisualStudio.TestTools.DataSource.CSV", "|DataDirectory|\\data\\Bai09.csv", "Bai09#csv", DataAccessMethod.Sequential)]
        public void ThayThe()
        {
            MethodLibrary.MethodLibrary m = new MethodLibrary.MethodLibrary();

            string s1 = TestContext.DataRow["s1"].ToString();
            if (s1 == "null") s1 = null;

            string s2 = TestContext.DataRow["s2"].ToString();
            if (s2 == "null") s2 = null;

            string s3 = TestContext.DataRow["s3"].ToString();
            if (s3 == "null") s3 = null;

            string exp = TestContext.DataRow["expected"].ToString();

            if (exp.ToLower() == "exception")
            {
                try
                {
                    m.ThayThe(s1, s2, s3);
                    Assert.Fail("Test case này mong đợi văng lỗi, nhưng hàm lại chạy bình thường.");
                }
                catch (Exception)
                {
                }
            }
            else
            {
                string act = m.ThayThe(s1, s2, s3);
                Assert.AreEqual(exp, act);
            }
        }
    }
}